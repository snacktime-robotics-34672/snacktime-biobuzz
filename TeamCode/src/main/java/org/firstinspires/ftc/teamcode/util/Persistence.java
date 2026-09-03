package org.firstinspires.ftc.teamcode.util;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import com.qualcomm.robotcore.util.ReadWriteFile;
import org.firstinspires.ftc.teamcode.config.FieldTweaks;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.subsystems.GameMechanism;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.firstinspires.ftc.teamcode.hardware.BuildInfo;
import org.firstinspires.ftc.teamcode.pedroPathing.PedroTuningStore;

import com.qualcomm.robotcore.hardware.VoltageSensor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persistence — two jobs (CLAUDE.md §7), both now ROBOT-AWARE (see {@link RobotIdentity}):
 *
 *   1. TUNING BACKUP (session persistence), PER ROBOT:
 *      {@link #saveTuning(RobotIdentity)} writes every registered tunable to that robot's own file.
 *      {@link #loadAndApplyTuning(RobotIdentity, Telemetry)} reads it back and applies the values
 *      to the live static fields on each class in TUNING_CLASSES. Call save on every OpMode stop;
 *      call load on every OpMode init.
 *
 *   2. SNAPSHOT (traceability record), PER ROBOT:
 *      {@link #writeSnapshot(Snapshot, HardwareMap)} writes a full JSON record (git hash, hardware,
 *      loop-time stats, every tunable) to a per-robot file, and also logs it via RobotLog so it
 *      appears in robotControllerLog.txt — grep "SNAPSHOT:" after any session.
 *
 * THE TWO-ROBOT MODEL (why the files are per-robot):
 *   The SAME commit runs on both the Competition robot and the Test bot (one codebase, never
 *   forked). They differ mainly in drivetrain tuning (mass & CG differ). Each robot keeps its own
 *   tuning file, on its own hub, read at init and written on stop:
 *     - Competition robot -> {@code comp_tuning.json}
 *     - Test bot          -> {@code testbot_tuning.json}
 *     - UNKNOWN identity   -> NO tuning file loaded or saved (fail closed). An unidentified hub is
 *       NEVER assumed to be the comp robot; it runs on the in-code defaults and says so loudly.
 *   Because the files are separate, nothing you tune on one robot can ever touch the other's values.
 *
 *   CANONICAL tuning = the COMMITTED per-robot files in git (repo `tuning/comp_tuning.json` and
 *   `tuning/testbot_tuning.json`). "Saving" a robot's tuning = pull its hub file into the repo and
 *   commit it — a whole-file commit, NO transcribing numbers into source. Both robots' tuning is
 *   backed up this way; neither is disposable. The in-code static defaults are only a LAST-RESORT
 *   fallback (a brand-new or freshly-reflashed hub before its file has been restored).
 *
 * HARD RULES (CLAUDE.md §7):
 *   - File I/O NEVER happens in the main loop — only on init, stop, or explicit button press.
 *   - loadAndApplyTuning MUST telemeter loudly when it finds and applies a file.
 *   - git is the real backup. A hub re-flash wipes hub files; the committed `tuning/` files don't.
 */
public final class Persistence {

    private static final String TAG = "Persistence";

    // Tuning file names, per robot (the on-hub filenames; the committed copies live in repo tuning/).
    // UNKNOWN deliberately has none — fail closed.
    static final String COMP_TUNING_FILE    = "comp_tuning.json";
    static final String TESTBOT_TUNING_FILE = "testbot_tuning.json";

    // All @Configurable classes whose public static fields are included in session persistence.
    // TuningConfig holds cross-cutting/drivetrain values; each mechanism subsystem holds its own.
    // KICKOFF: add each new @Configurable subsystem class here, e.g. GameMechanism.class.
    // Keys in the JSON are namespaced "ClassName.fieldName" to avoid collisions.
    private static final List<Class<?>> TUNING_CLASSES = Arrays.asList(
            TuningConfig.class,
            Drivetrain.class,
            JoystickCurve.class,
            // Field tweaks are measured on a PHYSICAL field and are expensive to re-measure, so
            // they must survive a restart like any other tuning.
            FieldTweaks.class,
            GameMechanism.class
            // KICKOFF: add each new @Configurable subsystem class here.
            // TuningClassRegistrationTest fails the build if you forget.
    );

    /** The registered tunable classes, for TunableWatcher's field table. */
    public static List<Class<?>> tuningClasses() {
        return TUNING_CLASSES;
    }

    /**
     * Simple names of every class in {@link #TUNING_CLASSES}.
     *
     * Exists so an off-robot test can check that every @Configurable class in teamcode is actually
     * registered here. Forgetting one used to be invisible: Panels shows the knob, the knob works,
     * and the value silently vanishes on every stop.
     */
    public static java.util.Set<String> tuningClassSimpleNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Class<?> cls : TUNING_CLASSES) names.add(cls.getSimpleName());
        return names;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    /** Everything worth persisting in a full record. Keep it a plain data holder. */
    public static class Snapshot {
        public String gitHash   = BuildInfo.GIT_HASH;
        public String buildTime = BuildInfo.BUILD_TIME;
        public long   savedAtSeconds = System.currentTimeMillis() / 1000L;

        // Which physical robot produced this snapshot, and the hub network name it was read from
        // (see RobotIdentity). Set by the OpMode; defaults are the fail-closed values.
        public String robot       = "UNKNOWN";
        public String networkName = "(unavailable)";

        public String alliance           = "UNKNOWN";
        public String startPose          = "UNKNOWN";
        public String lastKnownGoodPose  = "UNKNOWN";

        public double  startingBatteryVolts = 0.0;
        public boolean systemsCheckPassed   = false;
        public List<String> systemsCheckNotes = new ArrayList<>();

        // All devices from the RC hardware config: name → connection info (port/bus).
        // getConnectionInfo() is standard FTC SDK API on every HardwareDevice.
        public Map<String, String> hardware = new LinkedHashMap<>();

        // Loop-time stats from the OpMode's LoopTimer at write time — populated via captureLoop().
        // Watch these across sessions to spot regressions caused by code changes (§0 prime directive).
        public double avgLoopHz  = 0.0;  // smoothed average loop rate
        public double avgLoopMs  = 0.0;  // smoothed average cycle time
        public double maxLoopMs  = 0.0;  // worst single-cycle time since reset (tail latency)

        // Every registered tunable at the time of the write — auto-captured via reflection.
        // Keys are namespaced "ClassName.fieldName" so multiple @Configurable classes don't collide.
        public Map<String, Object> tuning = new LinkedHashMap<>();

        /** Populate loop stats from the OpMode's LoopTimer. Call once at stop, never in the loop. */
        public void captureLoop(LoopTimer timer) {
            double rawAvgMs = timer.getAverageMs();
            double rawAvgHz = rawAvgMs > 0.0 ? 1000.0 / rawAvgMs : 0.0;
            avgLoopMs = roundTo1Decimal(rawAvgMs);
            avgLoopHz = roundTo1Decimal(rawAvgHz);
            maxLoopMs = roundTo1Decimal(timer.getMaxLoopMs());
        }

        private static double roundTo1Decimal(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    /** AUTO-EXPORT with hardware capture. Safe on init and stop. NEVER in the loop. */
    public static void writeSnapshot(Snapshot snapshot, HardwareMap hardwareMap) {
        captureHardware(snapshot, hardwareMap);
        writeSnapshot(snapshot);
    }

    /** AUTO-EXPORT without hardware capture. Prefer the two-arg overload when hardwareMap is available. */
    public static void writeSnapshot(Snapshot snapshot) {
        captureTuningInto(snapshot.tuning);
        PedroTuningStore.captureInto(snapshot.tuning, identityFromName(snapshot.robot));
        try {
            // Per-robot filename so pulling snapshots from both robots into one folder never clobbers,
            // and each file is self-describing (see snapshotFileFor).
            File file = AppUtil.getInstance().getSettingsFile(snapshotFileFor(snapshot.robot));
            file.getParentFile().mkdirs();
            String json = GSON.toJson(snapshot);
            ReadWriteFile.writeFile(file, json);
            RobotLog.i("Persistence: snapshot OK → %s", file.getAbsolutePath());
            RobotLog.i("SNAPSHOT:%s", json);
        } catch (Throwable t) {
            RobotLog.e("Persistence: snapshot FAILED: %s", t.getMessage());
            Log.e(TAG, "Failed to write snapshot", t);
        }
    }

    // -------------------------------------------------------------------------
    // Tuning backup — session persistence
    // -------------------------------------------------------------------------

    /**
     * The tuning file name for a robot, or null if identity is UNKNOWN. Pure + package-private so it
     * can be unit-tested off-robot (§9). UNKNOWN returns null on purpose: fail closed — never
     * load/save tuning for a hub we can't identify.
     */
    public static String tuningFileFor(RobotIdentity.Robot robot) {
        switch (robot) {
            case COMPETITION: return COMP_TUNING_FILE;
            case TESTBOT:     return TESTBOT_TUNING_FILE;
            default:          return null; // UNKNOWN
        }
    }

    /** Per-robot snapshot file name. {@code robot} is Snapshot.robot (enum name, or "UNKNOWN"). Pure. */
    public static String snapshotFileFor(String robot) {
        String tag = (robot == null || robot.trim().isEmpty()) ? "UNKNOWN" : robot.trim();
        return "snacktime_snapshot_" + tag + ".json";
    }

    /**
     * Saves every registered tunable to THIS robot's tuning file, using namespaced
     * "ClassName.fieldName" keys. Call on every OpMode stop/reset. NEVER in the loop.
     * UNKNOWN identity saves nothing (fail closed) — we never want to persist tuning we can't
     * attribute to a specific robot.
     */
    public static synchronized void saveTuning(RobotIdentity id) {
        // SYNCHRONIZED because there are now two writers: an OpMode's stop() on the loop thread, and
        // the Pedro autosave daemon (TuningRecorder). Two threads writing the same file can
        // interleave into truncated JSON, which the next load would reject wholesale — losing the
        // tuning this feature exists to protect. The lock costs nothing: writes are rare and never
        // on the hot path.
        String fileName = tuningFileFor(id.robot);
        if (fileName == null) {
            RobotLog.i("Persistence: robot UNKNOWN — tuning NOT saved (fail closed)");
            return;
        }
        try {
            File file = AppUtil.getInstance().getSettingsFile(fileName);
            file.getParentFile().mkdirs();
            Map<String, Object> values = new LinkedHashMap<>();
            captureTuningInto(values);
            // Pedro's constants are not reflected over like the @Configurable classes above — they
            // live in nested Pedro types, so PedroTuningStore flattens them into plain doubles.
            // Same file: one per-robot file holds ALL of this robot's tuning.
            PedroTuningStore.captureInto(values, id);
            ReadWriteFile.writeFile(file, GSON.toJson(values));
            RobotLog.i("Persistence: %s tuning saved → %s", id.robot, file.getAbsolutePath());
        } catch (Throwable t) {
            RobotLog.e("Persistence: tuning save FAILED: %s", t.getMessage());
            Log.e(TAG, "Failed to save tuning", t);
        }
    }

    /**
     * Loads THIS robot's tuning file and applies every value back to the live tunable statics on each
     * class in TUNING_CLASSES. Telemeters loudly — required by CLAUDE.md §7.
     * Call on every OpMode init. NEVER in the loop.
     *
     * FAIL CLOSED: if identity is UNKNOWN, loads NOTHING and says so loudly — the robot runs on the
     * in-code fallback defaults. An unidentified hub is never given the comp robot's saved tuning by
     * accident, nor the test bot's.
     *
     * @return true if a tuning file was found and applied; false if running from code defaults.
     */
    public static boolean loadAndApplyTuning(RobotIdentity id, Telemetry telemetry) {
        String fileName = tuningFileFor(id.robot);
        if (fileName == null) {
            String msg = "ROBOT UNKNOWN [" + id.networkName + "] — NO tuning file loaded; running on "
                    + "in-code fallback defaults. Name the hub 34672-RC or 34672-T-RC.";
            if (telemetry != null) telemetry.addLine(msg);
            RobotLog.i("Persistence: %s", msg);
            return false;
        }
        try {
            File file = AppUtil.getInstance().getSettingsFile(fileName);
            if (!file.exists()) {
                // No tuning file on this hub yet — running on the in-code fallback defaults. Happens
                // on a brand-new or freshly-reflashed hub before its committed tuning/ file is
                // restored (adb push). Be honest about it rather than silent.
                String msg = String.format(Locale.US,
                        "%s: no tuning file yet (%s) — running on fallback defaults", id.robot, fileName);
                if (telemetry != null) telemetry.addLine(msg);
                RobotLog.i("Persistence: %s", msg);
                return false;
            }

            Map<String, Object> values = GSON.fromJson(
                    ReadWriteFile.readFile(file),
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (values == null || values.isEmpty()) return false;

            int applied = 0;
            int skipped = 0;
            for (Class<?> cls : TUNING_CLASSES) {
                String prefix = cls.getSimpleName() + ".";
                for (Field f : cls.getDeclaredFields()) {
                    int mods = f.getModifiers();
                    if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods)) continue;
                    Object val = values.get(prefix + f.getName());
                    if (val == null) continue;
                    try {
                        if (applyToField(f, val)) {
                            applied++;
                        } else {
                            // A tunable we can save but cannot restore. Never let this pass quietly:
                            // the robot would run on a code default while the file and the dashboard
                            // both showed the tuned number (§5 fail loud, §7 "it is loud").
                            skipped++;
                            RobotLog.ee("Persistence", "tuning NOT restored: %s%s is a %s, which "
                                            + "applyToField does not handle — running on the code "
                                            + "default instead",
                                    prefix, f.getName(), f.getType().getSimpleName());
                        }
                    } catch (Exception e) {
                        // Most likely an enum constant that was renamed or removed since the file
                        // was written. Keeping the code default is the safe direction, but say so.
                        skipped++;
                        RobotLog.ee("Persistence", "tuning NOT restored: %s%s — %s",
                                prefix, f.getName(), e);
                    }
                }
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(file.lastModified()));
            String msg = String.format(Locale.US,
                    "LOADED %s TUNING (%s, %s) — %d values", id.robot, fileName, timestamp, applied);
            if (skipped > 0) {
                // On the Driver Hub too, not just the log. A value that saved but did not restore
                // means the robot is running on something other than what the file says.
                msg += String.format(Locale.US, "  *** %d NOT RESTORED — see log ***", skipped);
            }
            telemetry.addLine(msg);
            RobotLog.i("Persistence: %s", msg);
            return true;

        } catch (Throwable t) {
            RobotLog.e("Persistence: tuning load FAILED: %s", t.getMessage());
            Log.e(TAG, "Failed to load tuning", t);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void captureHardware(Snapshot snap, HardwareMap hwMap) {
        snap.hardware.clear();
        try {
            List<String> names = new ArrayList<>(hwMap.getAllNames(HardwareDevice.class));
            Collections.sort(names);
            for (String name : names) {
                String info;
                try {
                    info = parseConnectionInfo(hwMap.get(HardwareDevice.class, name).getConnectionInfo());
                } catch (Throwable t) {
                    info = "unknown";
                }
                snap.hardware.put(name, info);
            }
        } catch (Throwable t) {
            snap.hardware.put("ERROR", "failed to enumerate: " + t.getMessage());
        }
    }

    /**
     * Reads this robot's tuning file and returns the raw key/value map, or null if there is nothing
     * usable. Does NOT apply anything — the caller decides what to do with it.
     *
     * This exists so Constants.createFollower can load Pedro values at the moment it builds the
     * follower, rather than depending on some OpMode having called loadAndApplyTuning first. NEVER
     * in the loop.
     */
    public static synchronized Map<String, Object> readTuningMap(RobotIdentity id) {
        String fileName = tuningFileFor(id.robot);
        if (fileName == null) return null;   // UNKNOWN hub: fail closed (§6)
        try {
            File file = AppUtil.getInstance().getSettingsFile(fileName);
            if (!file.exists()) return null;
            Map<String, Object> values = GSON.fromJson(
                    ReadWriteFile.readFile(file),
                    new TypeToken<Map<String, Object>>() {}.getType());
            return (values == null || values.isEmpty()) ? null : values;
        } catch (Throwable t) {
            RobotLog.e("Persistence: tuning read FAILED: %s", t.getMessage());
            return null;
        }
    }

    /**
     * Rebuilds a RobotIdentity from the robot name recorded on a Snapshot, so the snapshot can
     * carry Pedro values for the right robot. Returns null for an unrecognised name, which
     * PedroTuningStore treats as "record nothing" — fail closed, same as everywhere else.
     */
    private static RobotIdentity identityFromName(String robotName) {
        try {
            return RobotIdentity.of(RobotIdentity.Robot.valueOf(robotName), "(from snapshot)");
        } catch (Exception e) {
            return null;
        }
    }

    private static void captureTuningInto(Map<String, Object> map) {
        map.clear();
        for (Class<?> cls : TUNING_CLASSES) {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods)) continue;
                try { map.put(cls.getSimpleName() + "." + f.getName(), f.get(null)); }
                catch (Exception ignored) { }
            }
        }
    }

    /**
     * Applies a GSON-deserialized value to a tunable static field.
     * GSON always deserializes JSON numbers as Double, so we convert to the field's actual type.
     *
     * SAVING ALWAYS WORKS — {@code captureTuningInto} just calls {@code f.get(null)} and GSON
     * writes whatever it finds. Restoring is the half that has to know the type, so this is the only
     * place a tunable can go missing. It used to fall off the end of the if-chain and return
     * silently, which meant an unsupported type would save to the file, look right in the JSON, and
     * never come back — while the "LOADED ... N values" banner counted it as restored. Returning a
     * result instead lets the caller both count honestly and say something.
     *
     * Public only so it can be unit-tested off the robot (§9), same as the file-name helpers above.
     *
     * @return true if the value was applied; false if this field's type is not supported
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean applyToField(Field f, Object val) throws IllegalAccessException {
        Class<?> type = f.getType();

        if (type == double.class || type == Double.class) {
            f.set(null, ((Number) val).doubleValue());
        } else if (type == float.class || type == Float.class) {
            f.set(null, ((Number) val).floatValue());
        } else if (type == boolean.class || type == Boolean.class) {
            f.set(null, (Boolean) val);
        } else if (type == long.class || type == Long.class) {
            f.set(null, ((Number) val).longValue());
        } else if (type == int.class || type == Integer.class) {
            f.set(null, ((Number) val).intValue());
        } else if (type == String.class) {
            f.set(null, String.valueOf(val));
        } else if (!type.isPrimitive() && !(val instanceof String) && !type.isEnum()) {
            // A nested tunable object. Nothing registered uses one today — FieldTweaks was
            // flattened on 2026-09-02 precisely because nested values cannot be watched or restored
            // cheaply — so this is a safety net, not a live path. It stays because without it a
            // nested tunable saves, looks right in the JSON, and is reported NOT RESTORED on every
            // init. GSON already wrote the value as a nested map, so hand that map back to GSON and
            // let it rebuild the object. Still guarded: a shape GSON cannot rebuild throws and is
            // reported by the caller, same as any other failure.
            //
            // PREFER FLATTENING over relying on this. See the FieldTweaks class doc for why.
            f.set(null, GSON.fromJson(GSON.toJsonTree(val), type));
        } else if (type.isEnum()) {
            // GSON writes an enum as its constant name. valueOf throws if the constant was renamed
            // or removed since the file was written — the caller logs that and keeps the code
            // default, which is the safe direction.
            f.set(null, Enum.valueOf((Class<Enum>) type, String.valueOf(val)));
        } else {
            return false;
        }

        return true;
    }

    /** Extracts "port X" from raw SDK connection strings like "USB (embedded); module 173; port 0". */
    static String parseConnectionInfo(String raw) {
        if (raw == null) return "unknown";
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("port ")) return trimmed;
        }
        return raw.trim(); // fallback: return as-is if no port segment found
    }

    /** Reads the first voltage sensor safely; returns 0.0 if none found or read fails. */
    public static double readBatteryVolts(HardwareMap hardwareMap) {
        try {
            java.util.Iterator<VoltageSensor> it = hardwareMap.voltageSensor.iterator();
            if (it.hasNext()) return it.next().getVoltage();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private Persistence() { }
}
