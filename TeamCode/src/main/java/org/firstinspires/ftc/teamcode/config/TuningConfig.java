package org.firstinspires.ftc.teamcode.config;

import com.bylazar.configurables.annotations.Configurable;

/**
 * TuningConfig — cross-cutting configurables that apply globally, not to any one subsystem.
 *
 * Subsystem-specific tunables (drive speeds, PID gains, mechanism powers) live as public static
 * fields in each subsystem file with @Configurable on the class (§6 Tier 1). Panels groups them
 * by class name so they're easy to find. Add each subsystem to TUNING_CLASSES in Persistence.java.
 *
 * Fields must be public + static + non-final for the dashboard to see and edit them.
 */
@Configurable
public class TuningConfig {

    // Verbose subsystem telemetry is a BENCH tool. Leave OFF for matches so the loop allocates no
    // telemetry strings (prime directive §0, §4 rule 8). Flip ON live to watch subsystem health.
    public static boolean verboseTelemetry = false;

    // How long a Problem stays in the DiagnosticsCenter feed before it's cleaned up.
    public static long diagnosticsProblemExpireSeconds = 10;

    // Profiler.timeIt() logs per-block avg/min/max via RobotLog. BENCH TOOL — leave OFF for
    // matches (§4 rule 8). Flip on when investigating a loop-time regression.
    public static boolean profilerEnabled = false;

    /**
     * Whether a robot loads its saved Pedro tuning at init. ON by default — that is the point of
     * the autosave. Turn it OFF to make the robot run on the reviewed in-code constants in
     * Constants.java and ignore whatever is in the tuning file, which is the fastest way to answer
     * "is the file doing this, or is the code?" when path following looks wrong.
     */
    public static boolean pedroTuningLoadEnabled = true;

    /**
     * Whether EVERY OpMode watches tunables and saves them when they change. ON by default so a
     * value you turn on the bench cannot be lost to a crash or a pulled battery.
     *
     * CHANGED 2026-09-04: this is now the single switch for ALL autosave, in every OpMode, for both
     * kinds of tunable — the @Configurable statics and Pedro's constants. Before, the Pedro half ran
     * only inside the Tuning suite, so a pod offset turned in TeleOp was never queued for saving and
     * the next re-init read the file straight back over it. Turning this OFF now turns off the
     * Tuning suite's autosave as well, which is deliberate: one switch, no surprises about which
     * OpMode saves what.
     *
     * It costs 21 double compares for the Pedro values, plus one typed reflective read and one
     * compare per watched static — small, but not free. Turn it OFF for a match if you ever need
     * that budget back; saving on a clean stop still works either way.
     */
    public static boolean autosaveTunables = true;

    private TuningConfig() { } // static holder; never instantiated
}