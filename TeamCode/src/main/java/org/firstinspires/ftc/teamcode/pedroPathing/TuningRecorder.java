package org.firstinspires.ftc.teamcode.pedroPathing;

import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TuningRecorder — notices when you change a Pedro value, then saves it two ways.
 *
 * WHY IT EXISTS: Panels has no change hook. Its configurables module (checked against
 * com.bylazar.sloth:configurables 0.2.4.1+1.0.5) writes a field by reflection and notifies nobody —
 * there is no listener, callback, or observer to subscribe to. So "save when the value changes" has
 * to be poll-and-compare. This class is that poll.
 *
 * WHAT IT DOES when values change and then hold still:
 *   1. Writes a paste-ready Java block to the RC log, tagged PEDRO_TUNED. RC logs survive an app
 *      restart and a power cycle (CLAUDE.md §14), so a session is recoverable by hand even if the
 *      JSON never gets written.
 *   2. Queues a tuning-file save, so the values land in this robot's committed-tuning JSON.
 *
 * Two paths on purpose: the log is the human-readable backstop, the JSON is the automatic one. The
 * log costs nothing extra and has already proved to be the thing you want when the other half fails.
 *
 * WHY ON CHANGE, NOT ON STOP: {@code SelectableOpMode.stop()} is final and delegates to the selected
 * tuner, so the Tuning suite has no single stop seam. More importantly, an exception in a tuner does
 * not reach stop() at all — EventLoopManager catches it, rethrows as RobotCoreException, and goes to
 * EMERGENCY_STOP, which exits the event loop without calling callActiveOpModeStop(). Since
 * {@code Tuning.drawCurrent()} deliberately rethrows any drawing failure, that crash path is live in
 * this suite every loop. Change-detection survives it; a stop hook would not.
 *
 * LOOP COST: {@link PedroTuningStore#VALUE_COUNT} double compares against a cached array and no
 * allocation (CLAUDE.md §4.8). It builds a string, and hands off a file write, only in the moment
 * values settle after you actually turned a knob. Bench-only — the Tuning suite calls it, match
 * OpModes do not.
 *
 * FILE I/O NEVER RUNS ON THE LOOP THREAD. The loop sets a flag; a daemon thread does the write.
 */
public final class TuningRecorder {

    /** Log tag — grep this in robotControllerLog.txt to recover a session by hand. */
    public static final String TAG = "PEDRO_TUNED";

    /**
     * How long the values must hold still before we act, in nanoseconds.
     *
     * WHY A SETTLE DELAY: dragging a Panels slider walks through dozens of intermediate values. One
     * log line and one file write per value would bury the number you stopped on and hammer the
     * disk. Waiting for the values to hold still records where you landed, not the path you took.
     */
    private static final long SETTLE_NANOS = 1_000_000_000L;

    private static final int N = PedroTuningStore.VALUE_COUNT;

    // Last values seen. Flat double[] so the per-loop comparison allocates nothing.
    private static final double[] last = new double[N];
    private static final double[] scratch = new double[N];
    private static boolean primed = false;
    private static boolean dirty = false;
    private static long lastChangeNanos = 0L;

    // Set by the loop, cleared by the writer thread. Coalesces a burst of settles into one write.
    private static final AtomicBoolean savePending = new AtomicBoolean(false);
    private static volatile RobotIdentity saveIdentity = null;
    private static Thread writerThread = null;

    private TuningRecorder() {}

    /**
     * Call once per loop from the Tuning suite.
     *
     * @param id  the robot resolved at init — picks which constant set is watched
     * @param now {@code System.nanoTime()}, passed in so the settle logic can be tested off-robot
     */
    public static void poll(RobotIdentity id, long now) {
        if (id == null) return;

        FollowerConstants f = Constants.followerConstantsFor(id);
        MecanumConstants m = Constants.mecanumConstantsFor(id);
        PinpointConstants p = Constants.pinpointConstantsFor(id);

        PedroTuningStore.capture(scratch, f, m, p);

        if (!primed) {
            System.arraycopy(scratch, 0, last, 0, N);
            primed = true;
            return;
        }

        if (changed(scratch, last)) {
            System.arraycopy(scratch, 0, last, 0, N);
            dirty = true;
            lastChangeNanos = now;
            return;
        }

        if (dirty && now - lastChangeNanos >= SETTLE_NANOS) {
            dirty = false;
            RobotLog.ii(TAG, "%s tuning changed — paste this into Constants.java:%s",
                    id.robot, format(id.robot, last));
            queueSave(id);
        }
    }

    // ===================================================================================
    // The off-thread writer — keeps file I/O off the loop (CLAUDE.md §4 rule 3, §7)
    // ===================================================================================

    /**
     * Asks the writer thread to save this robot's tuning file. Returns immediately.
     *
     * The flag is the whole queue: if a write is already pending, a second settle inside the same
     * window costs nothing and the one write that happens picks up the newer values, because the
     * writer reads the live statics rather than a captured copy.
     */
    private static void queueSave(RobotIdentity id) {
        saveIdentity = id;
        savePending.set(true);
        startWriterOnce();
    }

    /** Starts the daemon writer on first use. Daemon so it can never hold the app open. */
    private static synchronized void startWriterOnce() {
        if (writerThread != null) return;
        writerThread = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    try {
                        Thread.sleep(250);
                        if (savePending.compareAndSet(true, false)) {
                            RobotIdentity id = saveIdentity;
                            if (id != null) Persistence.saveTuning(id);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable t) {
                        // A failed autosave must never take the thread down — the next settle
                        // should still get a chance to write. The paste-ready log block is the
                        // backstop if writes keep failing.
                        RobotLog.ee(TAG, "autosave failed: %s", t);
                    }
                }
            }
        }, "PedroTuningAutosave");
        writerThread.setDaemon(true);
        writerThread.setPriority(Thread.MIN_PRIORITY);
        writerThread.start();
    }

    // ===================================================================================
    // Pure helpers — public so they can be unit-tested off the robot (CLAUDE.md §9)
    // ===================================================================================

    /**
     * Builds the paste-ready Java block for one robot's tuned values.
     *
     * PURE. That matters because a wrong field name here produces a snippet that silently fails to
     * compile when a student pastes it at a competition.
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

    /** True if any tracked value moved. Exact compare on purpose: Panels writes exact doubles. Pure. */
    public static boolean changed(double[] a, double[] b) {
        for (int i = 0; i < N; i++) {
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
