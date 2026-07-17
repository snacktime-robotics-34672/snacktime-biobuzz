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

    // ---- Intake -------------------------------------------------------------------
    // Active intake: spinning wheels on a SINGLE motor. Powers are 0..1.
    public static double intakePower = 0.9;   // pulls a game piece IN
    public static double ejectPower = 0.7;    // pushes a game piece OUT

    // ---- Telemetry ----------------------------------------------------------------
    // Verbose subsystem telemetry is a BENCH tool. Leave OFF for matches so the loop allocates no
    // telemetry strings (prime directive section 0, section 4 rule 8). Flip it ON live on the
    // dashboard when you need to watch a subsystem's health while diagnosing on the bench.
    public static boolean verboseTelemetry = false;

    private TuningConfig() { } // static holder; never instantiated
}
