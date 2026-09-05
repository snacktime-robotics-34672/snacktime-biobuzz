package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.hardware.motors.MotorEx;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.teamcode.util.CurrentTracker;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.teamcode.config.TuningConfig;

/**
 * Drivetrain — mecanum drive with the per-wheel health telemetry section 5 requires.
 *
 * Pose/odometry is handled by Pedro (goBILDA Pinpoint, section 10) at the OpMode level; this
 * subsystem is just the four motors and their health. In auto, Pedro's follower drives; in teleop,
 * driveRobot is called from gamepad input.
 *
 * The Pinpoint is I2C — read it ONCE per loop at the OpMode level, never here (section 4, rule 5).
 */
@Configurable
public class Drivetrain extends SubsystemBase {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------
    public static double driveSpeedCap    = 1.0;   // 0..1, teleop full-speed multiplier
    public static double driveSlowModeCap = 0.35;  // 0..1, precision mode multiplier

    // ---- Stand your ground: hold position when the sticks are released ----------------------
    // The robot captures where it is and fights to stay there until the driver touches a stick.
    // See util/StandYourGround.java. Do NOT enable this together with headingHoldEnabled
    // below — Pedro's point-hold already governs heading, so the two fight over the same motors.

    public static boolean holdWhenIdleEnabled = true;

    // How long the sticks must sit at zero before the brace engages, in ms. This exists because the
    // robot is still coasting the moment the stick is released: hold instantly and it lurches
    // backwards to a pose it has already passed. Set to 0 to snap back to the exact release point.
    public static double holdEntryDelayMs = 250;

    // false makes the brace fight HARDER. Pedro scales hold corrections down by
    // holdPointTranslationalScaling / holdPointHeadingScaling (0.45 / 0.35 in FollowerConstants) so
    // a hold is gentle by default. Turn this off if an opponent can still shove us off the spot;
    // leave it on if the robot jitters or hunts around the held pose.
    public static boolean holdUseScaling = true;

    // Default timeout for DriveToPoseCommand, in seconds. Every command needs a timeout so nothing
    // can hang the robot for a whole match (§5). A single move that takes longer than this has gone
    // wrong — the command stops the robot and logs it. Individual moves can override with
    // .setTimeout(); raise this if a legitimately long drive keeps tripping it.
    public static double driveToPoseTimeoutSec = 5.0;

    // Same idea for FollowPathCommand, but longer: one timeout covers a whole PathChain, which may
    // be several segments. Override per-path with .setTimeout() for a genuinely long route.
    public static double followPathTimeoutSec = 15.0;

    // Heading-hold PIDF for TeleOp — resists drift when driver isn't turning.
    // TUNE ORDER: enable, raise headingHoldP until it resists; add headingHoldD if it oscillates; leave headingHoldI at 0.
    public static boolean headingHoldEnabled    = false;
    public static double  headingHoldNominalVoltage       = 12.4;  // voltage-compensate gains
    public static double  headingHoldThresholdMin = 0.05; // ignore corrections smaller than this
    public static double  headingHoldLagMs      = 200;   // ms to wait after stick release before engaging
    public static double  headingHoldP = 1.2;
    public static double  headingHoldI = 0;
    public static double  headingHoldD = 500;
    public static double  headingHoldF = 0;

    // ---- Drive current monitor --------------------------------------------------------------

    /**
     * Watch the total current the four drive motors pull. ON by default so it is there when you
     * want it, but it is NOT free: this is the one flag in this file that costs real loop time
     * (CLAUDE.md §0), so turn it off in Panels for a match.
     *
     * WHY IT COSTS: motor current is NOT part of the bulk read, unlike encoder positions. Checked
     * against the SDK — LynxDcMotorController.getMotorCurrent() has no bulk-cache path at all. It
     * builds a LynxGetADCCommand and blocks on sendReceive(). Four motors means four synchronous
     * round-trips to the hub, every loop, on top of everything else.
     *
     * So do not take my word for the cost: whenever this is on, TeleOp telemeters "Amp read ms",
     * measured. Watch it against the ~10ms budget and decide. Turning this off removes the reads
     * and the three amp readouts; nothing else changes.
     */
    public static boolean currentMonitorEnabled = true;

    /** Max and mean of the four-motor total. Reset at START so init readings do not skew it. */
    private final CurrentTracker driveCurrent = new CurrentTracker();

    /** What the four reads actually cost last loop, ms. Measured, not assumed (§0). */
    private double ampReadMs = 0.0;

    // Config names must match the Robot Controller configuration (section 10).
    private final MotorEx frontLeft;
    private final MotorEx frontRight;
    private final MotorEx backLeft;
    private final MotorEx backRight;

    public Drivetrain(HardwareMap hardwareMap) {
        frontLeft  = new MotorEx(hardwareMap, "LF_Motor");
        frontRight = new MotorEx(hardwareMap, "RF_Motor");
        backLeft   = new MotorEx(hardwareMap, "LR_Motor");
        backRight  = new MotorEx(hardwareMap, "RR_Motor");

        // TODO: set directions so +power drives forward on all wheels (typical mecanum below).
        // frontRight.setInverted(true);
        // backRight.setInverted(true);
    }

    /**
     * Robot-centric mecanum drive. All inputs are -1..1.
     *
     * @param drive  forward/back
     * @param strafe left/right
     * @param turn   rotation
     * @param cap    speed multiplier (0..1) — from TuningConfig, live-tunable
     */
    public void driveRobot(double drive, double strafe, double turn, double cap) {
        double fl = drive + strafe + turn;
        double fr = drive - strafe - turn;
        double bl = drive - strafe + turn;
        double br = drive + strafe - turn;

        // Normalize so no wheel exceeds 1.0, then apply the live speed cap (section 6).
        double max = 1.0;
        max = Math.max(max, Math.abs(fl));
        max = Math.max(max, Math.abs(fr));
        max = Math.max(max, Math.abs(bl));
        max = Math.max(max, Math.abs(br));

        frontLeft.set((fl / max) * cap);
        frontRight.set((fr / max) * cap);
        backLeft.set((bl / max) * cap);
        backRight.set((br / max) * cap);
    }

    /** Convenience overload using the standard speed cap. */
    public void driveRobot(double drive, double strafe, double turn) {
        driveRobot(drive, strafe, turn, driveSpeedCap);
    }

    public void stop() {
        driveRobot(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Reads all four motor currents and records the total.
     *
     * Read then process (§4 rule 2): the four reads happen together, then the sum goes in. Timing
     * the block costs two nanoTime calls, nothing against the four round-trips being measured.
     */
    private void readDriveCurrent() {
        long startNanos = System.nanoTime();
        double totalAmps = frontLeft.getCurrent(CurrentUnit.AMPS)
                + frontRight.getCurrent(CurrentUnit.AMPS)
                + backLeft.getCurrent(CurrentUnit.AMPS)
                + backRight.getCurrent(CurrentUnit.AMPS);
        ampReadMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        driveCurrent.add(totalAmps);
    }

    /** Total amps across all four drive motors, most recent reading. */
    public double getTotalAmps() { return driveCurrent.getLast(); }

    /** The largest total pulled since the last reset — the spike (a stall, a wall, a shove). */
    public double getMaxTotalAmps() { return driveCurrent.getMax(); }

    /** The average total since the last reset — the load, which is what drains the battery. */
    public double getMeanTotalAmps() { return driveCurrent.getMean(); }

    /** What the four current reads cost last loop, ms. Watch this against the §0 budget. */
    public double getAmpReadMs() { return ampReadMs; }

    /** Clears max and mean. Call at START so init-time readings do not count toward the match. */
    public void resetCurrentStats() { driveCurrent.reset(); }

    @Override
    public void periodic() {
        // Four blocking hub round-trips when on — see currentMonitorEnabled before leaving it on.
        if (currentMonitorEnabled) readDriveCurrent();

        // Per-wheel health telemetry (section 5) — VERBOSE ONLY so the match loop stays
        // allocation-free (prime directive section 0, section 4 rule 8). On the bench, flip
        // verboseTelemetry on and watch the four velocities for an outlier — exactly the view
        // that would have surfaced the back-left slip.
        if (TuningConfig.verboseTelemetry) {
            PanelsTelemetry.INSTANCE.getTelemetry().debug(
                    "FL pwr " + fmt(frontLeft.get()) + "  vel " + fmt(frontLeft.getCorrectedVelocity()));
            PanelsTelemetry.INSTANCE.getTelemetry().debug(
                    "FR pwr " + fmt(frontRight.get()) + "  vel " + fmt(frontRight.getCorrectedVelocity()));
            PanelsTelemetry.INSTANCE.getTelemetry().debug(
                    "BL pwr " + fmt(backLeft.get()) + "  vel " + fmt(backLeft.getCorrectedVelocity()));
            PanelsTelemetry.INSTANCE.getTelemetry().debug(
                    "BR pwr " + fmt(backRight.get()) + "  vel " + fmt(backRight.getCorrectedVelocity()));
        }
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }
}
