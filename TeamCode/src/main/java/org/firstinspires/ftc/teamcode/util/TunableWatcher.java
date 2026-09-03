package org.firstinspires.ftc.teamcode.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * TunableWatcher — notices when ANY registered tunable changes, and asks for a save.
 *
 * THE GAP IT CLOSES: the Pedro autosave only triggers on Pedro values. Change a servo position or a
 * speed cap on its own and nothing queued a save, so the value survived only if the OpMode reached
 * a clean stop. A crashed OpMode or a pulled battery lost it — the same failure the Pedro autosave
 * was built to prevent, just one class over.
 *
 * WHY POLLING: Panels has no change hook to subscribe to (checked against
 * com.bylazar.sloth:configurables 0.2.4.1+1.0.5 — no listener, callback, or observer anywhere), so
 * comparing values each loop is the only way to notice an edit.
 *
 * WHY IT ALLOCATES NOTHING (CLAUDE.md §4.8): the naive version reflects with {@code Field.get},
 * which boxes every primitive into an Object and hands the garbage collector a bag of work every
 * loop. The typed accessors — {@code getDouble}, {@code getInt}, {@code getBoolean} — return
 * primitives instead. So the Field array and the kind array are built ONCE, and each loop is only
 * typed reads into a reused double[] and a compare against another reused double[].
 *
 * WHAT IT WATCHES: primitive tunables (double, float, int, long, boolean) on every class in
 * Persistence.TUNING_CLASSES. Strings, enums, and nested objects are NOT watched — comparing them
 * cheaply is not possible, and they are rarely turned mid-session. They still save on OpMode stop
 * like they always have; only their change-triggered save is missing.
 *
 * COST: one typed reflective read plus one double compare per watched field per loop. Reflective
 * reads are slower than direct ones, so treat this as a bench tool: the Tuning suite runs it
 * always, and TeleOp runs it only while TuningConfig.autosaveTunables is true.
 */
public final class TunableWatcher {

    private static Field[] fields = null;
    private static byte[] kinds = null;
    private static double[] last = null;
    private static double[] scratch = null;

    private static boolean primed = false;
    private static boolean dirty = false;
    private static long lastChangeNanos = 0L;

    /** Values must hold still this long before we save — see TuningRecorder for why. */
    private static final long SETTLE_NANOS = 1_000_000_000L;

    private static final byte DOUBLE = 0, FLOAT = 1, INT = 2, LONG = 3, BOOLEAN = 4;

    private TunableWatcher() {}

    /**
     * Call once per loop. Queues a save about a second after a watched value stops changing.
     *
     * @param id  the robot resolved at init
     * @param now {@code System.nanoTime()}, passed in so the settle logic is testable off-robot
     */
    public static void poll(RobotIdentity id, long now) {
        if (id == null) return;
        if (fields == null) build();
        if (fields.length == 0) return;

        readInto(scratch);

        if (!primed) {
            System.arraycopy(scratch, 0, last, 0, fields.length);
            primed = true;
            return;
        }

        if (changed(scratch, last, fields.length)) {
            System.arraycopy(scratch, 0, last, 0, fields.length);
            dirty = true;
            lastChangeNanos = now;
            return;
        }

        if (dirty && now - lastChangeNanos >= SETTLE_NANOS) {
            dirty = false;
            TuningAutosave.request(id);
        }
    }

    /** Reads every watched field into {@code out} using typed accessors, so nothing is boxed. */
    private static void readInto(double[] out) {
        for (int i = 0; i < fields.length; i++) {
            try {
                switch (kinds[i]) {
                    case DOUBLE:  out[i] = fields[i].getDouble(null); break;
                    case FLOAT:   out[i] = fields[i].getFloat(null); break;
                    case INT:     out[i] = fields[i].getInt(null); break;
                    case LONG:    out[i] = fields[i].getLong(null); break;
                    case BOOLEAN: out[i] = fields[i].getBoolean(null) ? 1.0 : 0.0; break;
                    default:      out[i] = 0.0; break;
                }
            } catch (Throwable t) {
                // A field that cannot be read must not take down the loop. Hold the previous value
                // so it simply never looks changed.
                out[i] = last[i];
            }
        }
    }

    /**
     * True if any watched value moved. Exact compare: Panels writes exact values.
     * Pure, and public only so it can be unit-tested off the robot (CLAUDE.md §9).
     */
    public static boolean changed(double[] a, double[] b, int n) {
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) return true;
        }
        return false;
    }

    /** Builds the field table once. Called on the first poll, never in steady state. */
    private static synchronized void build() {
        if (fields != null) return;
        List<Field> found = new ArrayList<>();
        List<Byte> found_kinds = new ArrayList<>();
        for (Class<?> cls : Persistence.tuningClasses()) {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                Class<?> t = f.getType();
                byte kind;
                if (t == double.class)       kind = DOUBLE;
                else if (t == float.class)   kind = FLOAT;
                else if (t == int.class)     kind = INT;
                else if (t == long.class)    kind = LONG;
                else if (t == boolean.class) kind = BOOLEAN;
                else continue;               // String, enum, nested object — see the class doc
                found.add(f);
                found_kinds.add(kind);
            }
        }
        byte[] k = new byte[found_kinds.size()];
        for (int i = 0; i < k.length; i++) k[i] = found_kinds.get(i);
        kinds = k;
        last = new double[found.size()];
        scratch = new double[found.size()];
        fields = found.toArray(new Field[0]);
    }

    /** How many tunables are being watched. For telemetry and tests. */
    public static int watchedCount() {
        if (fields == null) build();
        return fields.length;
    }

    /** Forgets cached values so the next poll re-primes. Call when an OpMode initialises. */
    public static void reset() {
        primed = false;
        dirty = false;
    }
}
