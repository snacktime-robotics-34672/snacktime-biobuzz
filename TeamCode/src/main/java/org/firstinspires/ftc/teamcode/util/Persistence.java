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
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.firstinspires.ftc.teamcode.hardware.BuildInfo;

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
 * Persistence — two jobs (CLAUDE.md §7):
 *
 *   1. TUNING BACKUP (session persistence):
 *      {@link #saveTuning()} writes every registered tunable to current_tuning.json on the hub.
 *      {@link #loadAndApplyTuning(Telemetry)} reads it back and applies the values to the live
 *      static fields on each class in TUNING_CLASSES, so dashboard-tuned values survive across
 *      robot restarts. Call save on every OpMode stop; call load on every OpMode init.
 *      Limitation: a hub re-flash wipes the file — git is the real disaster backup (see below).
 *
 *   2. SNAPSHOT (traceability record):
 *      {@link #writeSnapshot(Snapshot, HardwareMap)} writes a full JSON record including git hash,
 *      hardware devices, loop-time stats (avg Hz + worst ms) so we can watch trends over time,
 *      and every registered tunable. Also logged via RobotLog so it appears in
 *      robotControllerLog.txt — grep "SNAPSHOT:" after any session to copy values back to source
 *      and commit them (that is the git disaster-backup workflow).
 *
 * HARD RULES (CLAUDE.md §7):
 *   - File I/O NEVER happens in the main loop — only on init, stop, or explicit button press.
 *   - loadAndApplyTuning MUST telemeter loudly when it finds and applies a file.
 *   - git is the real backup. A hub re-flash wipes hub files; git doesn't.
 */
public final class Persistence {

    private static final String SNAPSHOT_FILE = "snacktime_snapshot.json";
    private static final String TUNING_FILE   = "current_tuning.json";
    private static final String TAG           = "Persistence";

    // All @Configurable classes whose public static fields are included in session persistence.
    // TuningConfig holds cross-cutting/drivetrain values; each mechanism subsystem holds its own.
    // KICKOFF: add each new @Configurable subsystem class here, e.g. GameMechanism.class.
    // Keys in the JSON are namespaced "ClassName.fieldName" to avoid collisions.
    private static final List<Class<?>> TUNING_CLASSES = Arrays.asList(
            TuningConfig.class,
            Drivetrain.class,
            JoystickCurve.class
            // KICKOFF: add each new @Configurable subsystem class here, e.g. GameMechanism.class.
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    /** Everything worth persisting in a full record. Keep it a plain data holder. */
    public static class Snapshot {
        public String gitHash   = BuildInfo.GIT_HASH;
        public String buildTime = BuildInfo.BUILD_TIME;
        public long   savedAtSeconds = System.currentTimeMillis() / 1000L;

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
        try {
            File file = AppUtil.getInstance().getSettingsFile(SNAPSHOT_FILE);
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
     * Saves every registered tunable to current_tuning.json on the hub, using namespaced
     * "ClassName.fieldName" keys. Call on every OpMode stop/reset. NEVER in the loop.
     * Dashboard-modified values are saved here exactly as they are — they supersede source defaults
     * on the next load.
     */
    public static void saveTuning() {
        try {
            File file = AppUtil.getInstance().getSettingsFile(TUNING_FILE);
            file.getParentFile().mkdirs();
            Map<String, Object> values = new LinkedHashMap<>();
            captureTuningInto(values);
            ReadWriteFile.writeFile(file, GSON.toJson(values));
            RobotLog.i("Persistence: tuning saved → %s", file.getAbsolutePath());
        } catch (Throwable t) {
            RobotLog.e("Persistence: tuning save FAILED: %s", t.getMessage());
            Log.e(TAG, "Failed to save tuning", t);
        }
    }

    /**
     * Loads current_tuning.json and applies every value back to the live tunable statics on each
     * class in TUNING_CLASSES. Dashboard-tuned values supersede source defaults automatically.
     * Telemeters loudly when a file is found — required by CLAUDE.md §7.
     * Call on every OpMode init. NEVER in the loop.
     *
     * @return true if a tuning file was found and applied; false if running from source defaults.
     */
    public static boolean loadAndApplyTuning(Telemetry telemetry) {
        try {
            File file = AppUtil.getInstance().getSettingsFile(TUNING_FILE);
            if (!file.exists()) return false;

            Map<String, Object> values = GSON.fromJson(
                    ReadWriteFile.readFile(file),
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (values == null || values.isEmpty()) return false;

            int applied = 0;
            for (Class<?> cls : TUNING_CLASSES) {
                String prefix = cls.getSimpleName() + ".";
                for (Field f : cls.getDeclaredFields()) {
                    int mods = f.getModifiers();
                    if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods)) continue;
                    Object val = values.get(prefix + f.getName());
                    if (val == null) continue;
                    try {
                        applyToField(f, val);
                        applied++;
                    } catch (Exception ignored) { }
                }
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(file.lastModified()));
            String msg = String.format(Locale.US,
                    "LOADED TUNING FROM FILE (%s) — %d values", timestamp, applied);
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
     */
    private static void applyToField(Field f, Object val) throws IllegalAccessException {
        Class<?> type = f.getType();
        if (type == double.class || type == Double.class) {
            f.set(null, ((Number) val).doubleValue());
        } else if (type == boolean.class || type == Boolean.class) {
            f.set(null, (Boolean) val);
        } else if (type == long.class || type == Long.class) {
            f.set(null, ((Number) val).longValue());
        } else if (type == int.class || type == Integer.class) {
            f.set(null, ((Number) val).intValue());
        }
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
