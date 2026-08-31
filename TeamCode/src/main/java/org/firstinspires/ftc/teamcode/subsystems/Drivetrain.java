package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.hardware.motors.MotorEx;
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
    // See util/StandYourGround.java. Do NOT enable this together with headingCorrectionEnabled
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
    // TUNE ORDER: enable, raise headingP until it resists; add headingD if it oscillates; leave headingI at 0.
    public static boolean headingCorrectionEnabled    = false;
    public static double  headingNominalVoltage       = 12.4;  // voltage-compensate gains
    public static double  headingCorrectionThresholdMin = 0.05; // ignore corrections smaller than this
    public static double  headingCorrectionLagMs      = 200;   // ms to wait after stick release before engaging
    public static double  headingP = 1.2;
    public static double  headingI = 0;
    public static double  headingD = 500;
    public static double  headingF = 0;

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

    @Override
    public void periodic() {
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
