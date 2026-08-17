package org.firstinspires.ftc.teamcode.pedroPathing;

import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.FollowerBuilder;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;
import com.pedropathing.geometry.Pose;
import com.pedropathing.math.Vector;
import com.pedropathing.paths.PathConstraints;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * Constants — Pedro Pathing tuning, split PER ROBOT (CLAUDE.md §6, "Two robots, one codebase").
 *
 * WHY THE SPLIT: the Competition robot and the Test bot run the same commit, but they do not drive
 * the same. Mass and weight distribution differ, so their drive velocities, zero-power decelerations,
 * and PIDF gains differ too. One shared set would mean tuning on the test bot silently changes how
 * the comp robot follows a path — the exact failure this file now prevents.
 *
 * WHY THESE LIVE IN CODE, not in the tuning JSON: Pedro's tuners print a number you record once,
 * there are few of them, and the follower is built once at init and never re-read. Holding whole
 * {@code FollowerConstants} objects also stays robust across Pedro version bumps. Dashboard tunables
 * (the ones you turn every session) still live in the per-robot JSON — see Persistence.
 *
 * HOW TO TUNE (one robot at a time):
 *   1. Run the "Tuning" OpMode ON the robot you are tuning. Its banner names the robot.
 *   2. Record the printed number into THAT robot's set below — comp values in the comp fields,
 *      test values in the test fields. Never into both.
 *   3. Commit. git is the backup for these values (CLAUDE.md §12).
 *
 * WHAT IS SHARED: motor names and directions (the wiring is identical on both robots, CLAUDE.md §10)
 * and {@link #pathConstraints}. Everything else is split.
 */
public class Constants {

    // ===================================================================================
    // SHARED — same on both robots
    // ===================================================================================

    /**
     * Path end conditions (t-value, timeout, position and heading thresholds). These describe when
     * Pedro calls a path "done", not how hard the robot drives, so both robots use the same values.
     */
    public static PathConstraints pathConstraints = new PathConstraints(0.99, 100, 1, 1);

    // ===================================================================================
    // COMPETITION ROBOT
    // ===================================================================================

    /**
     * Comp follower tuning: mass, zero-power decelerations, and PIDF gains.
     * TODO: weigh the comp robot and set its real mass; 6.5 kg is carried over from the single
     * shared set and has not been confirmed against the fully-built comp robot.
     */
    public static FollowerConstants compFollowerConstants = new FollowerConstants()
            .mass(6.5);

    /**
     * Comp drive velocities, from ForwardVelocityTuner and LateralVelocityTuner.
     * TODO: run both tuners on the comp robot. These are still Pedro's stock defaults.
     */
    public static MecanumConstants compMecanumConstants = mecanumFor(81.34056, 65.43028, 1.0);

    /**
     * Comp Pinpoint pod offsets, from OffsetsTuner.
     * TODO: re-run OffsetsTuner on the comp robot. These offsets were measured 2026-07-18, before the
     * two-robot split existed, so which chassis they came from is not recorded — they are seeded into
     * both sets to keep today's behavior unchanged, not because they are known to be comp's.
     */
    public static PinpointConstants compPinpointConstants = pinpointFor(6.735, 0.287);

    // ===================================================================================
    // TEST BOT
    // ===================================================================================

    /**
     * Test-bot follower tuning.
     * TODO: weigh the test bot. A bare chassis is lighter than the comp robot, so this will not
     * stay at 6.5 kg.
     */
    public static FollowerConstants testFollowerConstants = new FollowerConstants()
            .mass(6.5);

    /** Test-bot drive velocities. TODO: run ForwardVelocityTuner and LateralVelocityTuner. */
    public static MecanumConstants testMecanumConstants = mecanumFor(81.34056, 65.43028, 1.0);

    /** Test-bot Pinpoint pod offsets. See the note on {@link #compPinpointConstants}. */
    public static PinpointConstants testPinpointConstants = pinpointFor(6.735, 0.287);

    // ===================================================================================
    // UNKNOWN HUB — fail closed (CLAUDE.md §5, §6)
    // ===================================================================================
    //
    // An UNKNOWN hub is a hub whose network name is neither robot's. We must still build a follower
    // — the OpMode cannot run without one — but we must not guess that it is the comp robot and hand
    // it comp's tuning. So UNKNOWN gets untuned Pedro defaults at HALF POWER: the robot still drives,
    // slowly and unsurprisingly, and createFollower() logs a warning naming the problem.

    public static FollowerConstants fallbackFollowerConstants = new FollowerConstants()
            .mass(6.5);

    /** Half power, so an unidentified robot cannot drive hard on tuning that may not be its own. */
    public static MecanumConstants fallbackMecanumConstants = mecanumFor(81.34056, 65.43028, 0.5);

    public static PinpointConstants fallbackPinpointConstants = pinpointFor(6.735, 0.287);

    // ===================================================================================
    // Builder
    // ===================================================================================

    /**
     * Builds the follower for the robot we are actually running on.
     *
     * @param hardwareMap the OpMode's hardware map
     * @param id          resolved once at init by {@link RobotIdentity#resolve()} — pass it in rather
     *                    than resolving here, so one OpMode reads the hub name exactly once
     */
    public static Follower createFollower(HardwareMap hardwareMap, RobotIdentity id) {
        FollowerConstants follower;
        MecanumConstants drivetrain;
        PinpointConstants localizer;

        switch (id.robot) {
            case COMPETITION:
                follower = compFollowerConstants;
                drivetrain = compMecanumConstants;
                localizer = compPinpointConstants;
                break;
            case TESTBOT:
                follower = testFollowerConstants;
                drivetrain = testMecanumConstants;
                localizer = testPinpointConstants;
                break;
            default:
                follower = fallbackFollowerConstants;
                drivetrain = fallbackMecanumConstants;
                localizer = fallbackPinpointConstants;
                RobotLog.ww("PedroConstants", "UNKNOWN robot (name=\"%s\") — using untuned fallback "
                        + "Pedro constants at %.2f max power. Path following will be inaccurate.",
                        id.networkName, fallbackMecanumConstants.maxPower);
                break;
        }

        RobotLog.ii("PedroConstants", "follower built for %s: mass=%.2f kg, xVel=%.3f, yVel=%.3f, "
                        + "forwardPodY=%.3f, strafePodX=%.3f",
                id.robot, follower.mass, drivetrain.xVelocity, drivetrain.yVelocity,
                localizer.forwardPodY, localizer.strafePodX);

        return new FollowerBuilder(follower, hardwareMap)
                .mecanumDrivetrain(drivetrain)
                .pinpointLocalizer(localizer)
                .pathConstraints(pathConstraints)
                .build();
    }

    // ===================================================================================
    // Helpers — keep the shared wiring in ONE place so the two robots can never drift apart
    // ===================================================================================

    /**
     * Builds a MecanumConstants with our shared wiring and the given per-robot drive velocities.
     *
     * WHY A HELPER: motor names and directions are identical on both robots, so writing them twice
     * would let a fix land on one robot and not the other. Only the tuned numbers are arguments.
     *
     * WHY frontLeftVector IS SET BY HAND: Pedro derives that vector — the direction the mecanum
     * wheels actually prefer to drive — from xVelocity and yVelocity, but it only does that derivation
     * in its own constructor. Setting the velocities afterwards leaves the vector at Pedro's stock
     * ratio, so tuned velocities would be half-ignored. Recomputing it here with Pedro's own formula
     * keeps the vector consistent with the velocities we just set.
     *
     * @param xVelocity max forward velocity, in/s — from ForwardVelocityTuner
     * @param yVelocity max lateral velocity, in/s — from LateralVelocityTuner
     * @param maxPower  power ceiling, 0..1
     */
    private static MecanumConstants mecanumFor(double xVelocity, double yVelocity, double maxPower) {
        MecanumConstants constants = new MecanumConstants()
                .leftFrontMotorName("LF_Motor")
                .leftRearMotorName("LR_Motor")
                .rightFrontMotorName("RF_Motor")
                .rightRearMotorName("RR_Motor")
                .leftFrontMotorDirection(DcMotorSimple.Direction.REVERSE)
                .leftRearMotorDirection(DcMotorSimple.Direction.REVERSE)
                .rightFrontMotorDirection(DcMotorSimple.Direction.FORWARD)
                .rightRearMotorDirection(DcMotorSimple.Direction.FORWARD)
                .xVelocity(xVelocity)
                .yVelocity(yVelocity)
                .maxPower(maxPower);

        double[] polar = Pose.cartesianToPolar(xVelocity, -yVelocity);
        constants.setFrontLeftVector(new Vector(polar[0], polar[1]).normalize());
        return constants;
    }

    /**
     * Builds a PinpointConstants with our shared device config and the given per-robot pod offsets.
     *
     * @param forwardPodY forward pod's Y offset from robot center, inches — from OffsetsTuner
     * @param strafePodX  strafe pod's X offset from robot center, inches — from OffsetsTuner
     */
    private static PinpointConstants pinpointFor(double forwardPodY, double strafePodX) {
        return new PinpointConstants()
                .hardwareMapName("pinpoint")
                .encoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD)
                .forwardPodY(forwardPodY)
                .strafePodX(strafePodX);
    }
}