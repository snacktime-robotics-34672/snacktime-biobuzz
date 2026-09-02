package org.firstinspires.ftc.teamcode.pedroPathing;

import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * TuningRecorder — makes a Pedro tuning session survive the robot losing power.
 *
 * THE PROBLEM IT SOLVES: Pedro constants are live-editable in Panels but are NOT in the tuning JSON
 * (CLAUDE.md §6 — they are recorded into Constants.java and committed; git is the save path). That
 * is deliberate, but it left a trap: a gain you turn in Panels lives only in RAM. Close the
 * dashboard or restart the Robot Controller and the whole session is gone, with nothing to
 * transcribe from. That is exactly how the 2026-09-01 test-bot PIDF session was lost.
 *
 * WHAT IT DOES: watches the active robot's tuned values each loop and, whenever they settle after a
 * change, writes ONE paste-ready Java block to the Robot Controller log. RC logs are durable — they
 * survive an app restart and a power cycle (CLAUDE.md §14) — so the numbers are recoverable hours
 * later, from the pits, with the robot off. Recovering a session is then: pull the log, grep
 * PEDRO_TUNED, paste the block into {@link Constants}, hot-reload, commit.
 *
 * WHY ON CHANGE, NOT ON STOP: {@code SelectableOpMode.stop()} is final and delegates to the selected
 * tuner, so the Tuning suite has no single stop seam to hook — the same reason
 * {@link Tuning#drawCurrent} is where live-apply lives. Logging on change is also strictly safer:
 * a clean stop is the one path a dead battery or a crashed app never takes.
 *
 * COST: on the steady path this is a handful of double compares against a cached array and no
 * allocation at all (CLAUDE.md §4.8). It builds a string only in the moment values settle after you
 * actually turn a knob. It runs in the Tuning suite only, never in a match OpMode.
 *
 * HOW TO TELL IT IS WORKING: turn any gain in Panels, wait a second, then look for a line tagged
 * PEDRO_TUNED in logcat or robotControllerLog.txt.
 */
public final class TuningRecorder {

    /** Log tag — grep this in robotControllerLog.txt to recover a session. */
    public static final String TAG = "PEDRO_TUNED";

    /**
     * How long the values must hold still before we log them, in nanoseconds.
     *
     * WHY A SETTLE DELAY: dragging a Panels slider walks through dozens of intermediate values. One
     * log line per value would bury the number you actually stopped on. Waiting for the values to
     * hold still means the log records where you landed, not the path you took.
     */
    private static final long SETTLE_NANOS = 1_000_000_000L;

    /** Number of tracked values. Must match {@link #capture} and {@link #format} exactly. */
    public static final int VALUE_COUNT = 21;

    // Last values seen, and whether they have changed since we last logged. Kept as a flat double[]
    // so the per-loop comparison allocates nothing.
    private static final double[] last = new double[VALUE_COUNT];
    private static final double[] scratch = new double[VALUE_COUNT];
    private static boolean primed = false;
    private static boolean dirty = false;
    private static long lastChangeNanos = 0L;

    private TuningRecorder() {}

    /**
     * Reads the active robot's tuned values into {@code out}. Allocation-free.
     *
     * The order here is the contract between {@link #capture} and {@link #format}. Change one and
     * you must change the other; {@link #VALUE_COUNT} guards the length.
     */
    public static void capture(double[] out, FollowerConstants f, MecanumConstants m, PinpointConstants p) {
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

        // Pod offsets are not live (the localizer reads them once), but they belong in the record:
        // OffsetsTuner prints them and they are just as easy to lose.
        out[19] = p.forwardPodY;
        out[20] = p.strafePodX;
    }

    /**
     * Builds the paste-ready Java block for one robot's tuned values.
     *
     * PURE — no hardware, no logging, no statics read. That is what lets it be unit-tested off the
     * robot (CLAUDE.md §9), which matters because a wrong field name here produces a snippet that
     * silently does not compile when a student pastes it at a competition.
     */
    public static String format(RobotIdentity.Robot robot, double[] v) {
        String set = fieldPrefixFor(robot);
        return "\n"
                + "// ---- paste into Constants.java, replacing the " + set + " set ----\n"
                + "public static FollowerConstants " + set + "FollowerConstants = new FollowerConstants()\n"
                + "        .mass(" + v[14] + ")\n"
                + "        .forwardZeroPowerAcceleration(" + v[15] + ")\n"
                + "        .lateralZeroPowerAcceleration(" + v[16] + ")\n"
                + "        .centripetalScaling(" + v[13] + ")\n"
                + "        .translationalPIDFCoefficients(new PIDFCoefficients("
                + v[0] + ", " + v[1] + ", " + v[2] + ", " + v[3] + "))\n"
                + "        .headingPIDFCoefficients(new PIDFCoefficients("
                + v[4] + ", " + v[5] + ", " + v[6] + ", " + v[7] + "))\n"
                + "        .drivePIDFCoefficients(new FilteredPIDFCoefficients("
                + v[8] + ", " + v[9] + ", " + v[10] + ", " + v[11] + ", " + v[12] + "));\n"
                + "public static MecanumConstants " + set + "MecanumConstants = mecanumFor("
                + v[17] + ", " + v[18] + ", 1.0);\n"
                + "public static PinpointConstants " + set + "PinpointConstants = pinpointFor("
                + v[19] + ", " + v[20] + ");\n"
                + "// ---- end " + set + " ----";
    }

    /** Maps a robot to the {@link Constants} field-name prefix its values belong in. Pure. */
    public static String fieldPrefixFor(RobotIdentity.Robot robot) {
        switch (robot) {
            case COMPETITION: return "comp";
            case TESTBOT:     return "test";
            default:          return "fallback";
        }
    }

    /**
     * Call once per loop from the Tuning suite. Logs a paste-ready block whenever the tuned values
     * change and then hold still for {@link #SETTLE_NANOS}.
     *
     * @param id  the robot resolved at init — picks which constant set is watched
     * @param now {@code System.nanoTime()}, passed in so the settle logic can be tested off-robot
     */
    public static void poll(RobotIdentity id, long now) {
        if (id == null) return;

        FollowerConstants f = Constants.followerConstantsFor(id);
        MecanumConstants m = Constants.mecanumConstantsFor(id);
        PinpointConstants p = Constants.pinpointConstantsFor(id);

        capture(scratch, f, m, p);

        if (!primed) {
            System.arraycopy(scratch, 0, last, 0, VALUE_COUNT);
            primed = true;
            return;
        }

        if (changed(scratch, last)) {
            System.arraycopy(scratch, 0, last, 0, VALUE_COUNT);
            dirty = true;
            lastChangeNanos = now;
            return;
        }

        if (dirty && now - lastChangeNanos >= SETTLE_NANOS) {
            dirty = false;
            RobotLog.ii(TAG, "%s tuning changed — paste this into Constants.java:%s",
                    id.robot, format(id.robot, last));
        }
    }

    /**
     * True if any tracked value moved. Exact compare on purpose: Panels writes exact doubles.
     * Pure, and public only so it can be unit-tested off the robot (CLAUDE.md §9).
     */
    public static boolean changed(double[] a, double[] b) {
        for (int i = 0; i < VALUE_COUNT; i++) {
            if (a[i] != b[i]) return true;
        }
        return false;
    }

    /** Forgets the cached values, so the next {@link #poll} re-primes. Call when a follower is built. */
    public static void reset() {
        primed = false;
        dirty = false;
    }
}
