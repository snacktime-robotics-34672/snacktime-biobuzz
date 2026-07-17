package org.firstinspires.ftc.teamcode.config;

import com.bylazar.configurables.annotations.Configurable;

/**
 * TuningConfig — every number you might adjust at a competition lives here.
 *
 * CLAUDE.md section 6 (Reload & configuration model), Tier 1:
 *   These are LIVE CONFIGURABLES. With Panels open you can change any of these on the dashboard in
 *   real time with NO code push at all. Never hardcode a tunable number in a subsystem or OpMode.
 *
 * IMPORTANT (CLAUDE.md section 6, "Promote good values back to source"):
 *   Values tuned live on the dashboard are RUNTIME-ONLY. Once a value is dialed in, copy it back
 *   here as the new default and commit it, or it is lost on the next app restart. The snapshot
 *   written by Persistence (section 7) makes this a copy-paste, not a transcription.
 *
 * Fields must be public + static + non-final for the dashboard to see and edit them.
 * (Verify the exact annotation import against your installed Panels version.)
 */
@Configurable
public class TuningConfig {

    // ---- Drivetrain ---------------------------------------------------------------
    public static double driveSpeedCap = 1.0;      // 0..1, teleop speed multiplier
    public static double driveSlowModeCap = 0.35;  // 0..1, precision mode multiplier
    public static double driveDeadzone = 0.05;     // 0..1, stick inputs below this are treated as zero

    // ---- Heading Correction (used by HeadingCorrector) ----------------------------
    // Optional PIDF heading-hold for TeleOp: robot resists heading drift when the driver isn't
    // actively turning. Leave OFF until on-robot testing; tune HP first, then HD if it oscillates.
    public static boolean headingCorrectionEnabled = false;
    public static double headingNominalVoltage = 12.4;         // voltage-compensate gains
    public static double headingCorrectionThresholdMin = 0.05; // dead below this correction magnitude
    public static double headingCorrectionLagMs = 200;         // wait after stick release before engaging
    public static double headingP = 1.2;
    public static double headingI = 0;
    public static double headingD = 500;
    public static double headingF = 0;

    // ---- Game mechanism tunables go here at kickoff --------------------------------
    // Follow the drivetrain pattern above: public static double, non-final, with units.

    // ---- Telemetry ----------------------------------------------------------------
    // Verbose subsystem telemetry is a BENCH tool. Leave OFF for matches so the loop allocates no
    // telemetry strings (prime directive section 0, section 4 rule 8). Flip it ON live on the
    // dashboard when you need to watch a subsystem's health while diagnosing on the bench.
    public static boolean verboseTelemetry = false;

    // ---- Diagnostics --------------------------------------------------------------
    // How long a Problem stays in the DiagnosticsCenter feed before it's cleaned up. Longer =
    // more history on the Driver Hub; shorter = only current problems.
    public static long diagnosticsProblemExpireSeconds = 10;

    // Profiler.timeIt() logs per-block avg/min/max via RobotLog when this is on. BENCH TOOL —
    // leave OFF for matches (§4 rule 8, log I/O adds to loop budget). Flip on when investigating
    // a specific loop-time regression.
    public static boolean profilerEnabled = false;

    private TuningConfig() { } // static holder; never instantiated
}
