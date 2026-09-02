package org.firstinspires.ftc.teamcode.pedroPathing;

import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.util.RobotIdentity;

import java.util.Locale;
import java.util.Map;

/**
 * PedroTuningStore — the one table of Pedro tuning values, and the JSON save/load built on it.
 *
 * WHY ONE TABLE: three things need to agree on which values are "the Pedro tuning" — the
 * PEDRO_TUNED log block, the JSON save, and the JSON load. Three separate lists would drift, and a
 * drifted list fails silently: a value saves but never restores, and the robot drives on a code
 * default while the file and the dashboard both show the tuned number. So {@link #KEYS},
 * {@link #capture} and {@link #restore} live next to each other and are read in the same order.
 *
 * WHY FLAT DOUBLE KEYS ("Pedro.mass", not a nested object): every value written here is a plain
 * double, so {@code Persistence.applyToField} restores it with no new type handling. Pedro's own
 * types are nested (PIDFCoefficients inside FollowerConstants), and teaching the restore path to
 * walk nested objects would add code to the exact place the codebase warns is the only place a
 * tunable can go missing. Flattening moves that risk into this table, where it is explicit and
 * unit-tested.
 *
 * WHAT IS NOT HERE: {@code Constants.pathConstraints} is shared by both robots, so it does not fit
 * a per-robot file and stays in code. Motor names and directions are wiring, not tuning.
 *
 * SAFETY: {@link #applyFrom} is all-or-nothing. If any value in the file is missing, unparseable,
 * or out of range, the whole Pedro block is rejected and the robot runs on the in-code defaults,
 * loudly. A partial restore — some values from the file, some from code — is the worst outcome,
 * because nothing on the robot would tell you which is which.
 */
public final class PedroTuningStore {

    /** Prefix for every key this class owns, so it cannot collide with a @Configurable class name. */
    public static final String PREFIX = "Pedro.";

    /**
     * The tuned values, in capture order. {@link #capture} and {@link #restore} MUST read and write
     * these in exactly this order — the index is the contract between them.
     */
    public static final String[] KEYS = {
            "translationalP", "translationalI", "translationalD", "translationalF",
            "headingP", "headingI", "headingD", "headingF",
            "driveP", "driveI", "driveD", "driveT", "driveF",
            "centripetalScaling",
            "mass",
            "forwardZeroPowerAcceleration",
            "lateralZeroPowerAcceleration",
            "xVelocity", "yVelocity",
            "forwardPodY", "strafePodX",
    };

    public static final int VALUE_COUNT = KEYS.length;

    private PedroTuningStore() {}

    // ===================================================================================
    // The table — capture and its exact inverse
    // ===================================================================================

    /** Reads the live constants into {@code out}. Allocation-free, so the loop can call it. Pure. */
    public static void capture(double[] out,
                               FollowerConstants f, MecanumConstants m, PinpointConstants p) {
        out[0]  = f.coefficientsTranslationalPIDF.P;
        out[1]  = f.coefficientsTranslationalPIDF.I;
        out[2]  = f.coefficientsTranslationalPIDF.D;
        out[3]  = f.coefficientsTranslationalPIDF.F;

        out[4]  = f.coefficientsHeadingPIDF.P;
        out[5]  = f.coefficientsHeadingPIDF.I;
        out[6]  = f.coefficientsHeadingPIDF.D;
        out[7]  = f.coefficientsHeadingPIDF.F;

        out[8]  = f.coefficientsDrivePIDF.P;
        out[9]  = f.coefficientsDrivePIDF.I;
        out[10] = f.coefficientsDrivePIDF.D;
        out[11] = f.coefficientsDrivePIDF.T;
        out[12] = f.coefficientsDrivePIDF.F;

        out[13] = f.centripetalScaling;
        out[14] = f.mass;
        out[15] = f.forwardZeroPowerAcceleration;
        out[16] = f.lateralZeroPowerAcceleration;

        out[17] = m.xVelocity;
        out[18] = m.yVelocity;

        out[19] = p.forwardPodY;
        out[20] = p.strafePodX;
    }

    /**
     * Writes {@code v} back into the live constants — the exact inverse of {@link #capture}.
     *
     * Pod offsets are written even though the localizer reads them only when it is built: this runs
     * inside {@link Constants#createFollower} BEFORE the build, so they do take effect.
     */
    public static void restore(double[] v,
                               FollowerConstants f, MecanumConstants m, PinpointConstants p) {
        f.coefficientsTranslationalPIDF.setCoefficients(v[0], v[1], v[2], v[3]);
        f.coefficientsHeadingPIDF.setCoefficients(v[4], v[5], v[6], v[7]);
        // Pedro's filtered setter takes (p, i, d, t, f) — T before F, unlike the field order.
        f.coefficientsDrivePIDF.setCoefficients(v[8], v[9], v[10], v[11], v[12]);

        f.centripetalScaling = v[13];
        f.mass = v[14];
        f.forwardZeroPowerAcceleration = v[15];
        f.lateralZeroPowerAcceleration = v[16];

        m.xVelocity = v[17];
        m.yVelocity = v[18];

        p.forwardPodY = v[19];
        p.strafePodX = v[20];
    }

    // ===================================================================================
    // Validation — reject a bad file rather than drive on half of it
    // ===================================================================================

    /**
     * True if every value is finite and inside a physically sensible range. Pure.
     *
     * The ranges are deliberately wide. This is not a tuning opinion — it is a guard against a
     * corrupt or truncated file turning into a robot that drives into a wall at full power. A value
     * a human actually tuned will never come close to these bounds.
     */
    public static boolean isSane(double[] v) {
        if (v == null || v.length != VALUE_COUNT) return false;
        for (double d : v) {
            if (Double.isNaN(d) || Double.isInfinite(d)) return false;
        }
        for (int i = 0; i <= 12; i++) {          // PIDF gains
            if (Math.abs(v[i]) > 1000.0) return false;
        }
        if (Math.abs(v[13]) > 10.0) return false;         // centripetal scaling
        if (v[14] <= 0.0 || v[14] > 100.0) return false;  // mass, kg — a robot is not massless
        if (Math.abs(v[15]) > 1000.0) return false;       // zero-power accelerations, in/s^2
        if (Math.abs(v[16]) > 1000.0) return false;
        if (v[17] <= 0.0 || v[17] > 500.0) return false;  // drive velocities, in/s — must be positive
        if (v[18] <= 0.0 || v[18] > 500.0) return false;
        if (Math.abs(v[19]) > 20.0) return false;         // pod offsets, inches from robot center
        if (Math.abs(v[20]) > 20.0) return false;
        return true;
    }

    /**
     * Reads the Pedro block out of a loaded tuning map into {@code out}.
     * Pure — no file access, no statics, so it is unit-testable off the robot (CLAUDE.md §9).
     *
     * @return true if every key was present and numeric; false if the block is missing or partial.
     */
    public static boolean readInto(double[] out, Map<String, Object> values) {
        if (values == null) return false;
        for (int i = 0; i < VALUE_COUNT; i++) {
            Object raw = values.get(PREFIX + KEYS[i]);
            if (!(raw instanceof Number)) return false;
            out[i] = ((Number) raw).doubleValue();
        }
        return true;
    }

    // ===================================================================================
    // Save and load — the robot-facing half
    // ===================================================================================

    /** Adds this robot's Pedro values to the tuning map, under {@link #PREFIX} keys. */
    public static void captureInto(Map<String, Object> values, RobotIdentity id) {
        if (id == null) return;
        double[] v = new double[VALUE_COUNT];
        capture(v,
                Constants.followerConstantsFor(id),
                Constants.mecanumConstantsFor(id),
                Constants.pinpointConstantsFor(id));
        for (int i = 0; i < VALUE_COUNT; i++) {
            values.put(PREFIX + KEYS[i], v[i]);
        }
    }

    /** What the last {@link #applyFrom} did, for the Driver Hub. Never null. */
    private static String lastStatus = "Pedro tuning: not loaded yet";

    /** One line describing the last load, safe to telemeter. */
    public static String lastStatus() { return lastStatus; }

    /**
     * Applies a loaded tuning map to this robot's Pedro constants. All-or-nothing.
     *
     * @return true if the file's values were applied; false if the robot is on in-code defaults.
     */
    public static boolean applyFrom(Map<String, Object> values, RobotIdentity id) {
        if (id == null || !id.isKnown()) {
            lastStatus = "Pedro tuning: NOT loaded (robot UNKNOWN) — in-code defaults";
            RobotLog.ww("PedroTuning", "%s", lastStatus);
            return false;
        }

        double[] v = new double[VALUE_COUNT];
        if (!readInto(v, values)) {
            lastStatus = "Pedro tuning: none in file — in-code defaults";
            RobotLog.ii("PedroTuning", "%s", lastStatus);
            return false;
        }
        if (!isSane(v)) {
            lastStatus = "Pedro tuning: REJECTED (values out of range) — in-code defaults";
            RobotLog.ee("PedroTuning", "%s", lastStatus);
            return false;
        }

        restore(v,
                Constants.followerConstantsFor(id),
                Constants.mecanumConstantsFor(id),
                Constants.pinpointConstantsFor(id));

        lastStatus = String.format(Locale.US, "LOADED %s PEDRO TUNING — %d values", id.robot, VALUE_COUNT);
        RobotLog.ii("PedroTuning", "%s", lastStatus);
        return true;
    }

    /**
     * Logs what actually reached the follower, read back out of the built object.
     *
     * WHY READ BACK instead of logging what we restored: decode-2025 persisted four Pedro values
     * whose builder lines were commented out (pedro/Constants.java:95-99), so they saved perfectly
     * and never reached the follower — knobs that looked tuned and changed nothing. Reading the
     * values back out of the follower Pedro is actually holding is the only check that catches a
     * wiring gap like that. Init-time only, never the loop.
     */
    public static void logAsBuilt(FollowerConstants f, MecanumConstants m, PinpointConstants p) {
        double[] v = new double[VALUE_COUNT];
        capture(v, f, m, p);
        StringBuilder sb = new StringBuilder("as built: ");
        for (int i = 0; i < VALUE_COUNT; i++) {
            if (i > 0) sb.append(", ");
            sb.append(KEYS[i]).append('=').append(v[i]);
        }
        RobotLog.ii("PedroTuning", "%s", sb.toString());
    }
}
