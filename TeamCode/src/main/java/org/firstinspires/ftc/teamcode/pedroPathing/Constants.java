package org.firstinspires.ftc.teamcode.pedroPathing;

import com.bylazar.configurables.annotations.Configurable;
import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * Constants — Pedro Pathing tuning, split PER ROBOT (CLAUDE.md §6, "Two robots, one codebase").
 *
 * WHY THE SPLIT: the Competition robot and the Test bot run the same commit, but they do not drive
 * the same. Mass and weight distribution differ, so their drive velocities, braking, and gains
 * differ too. One shared set would mean tuning on the test bot silently changes how the comp robot
 * follows a path.
 *
 * ---------------------------------------------------------------------------------------------
 * PEDRO 3 CHANGED HOW THIS FILE WORKS (2026-09-22). Read this before editing.
 *
 * Pedro 2 had constants OBJECTS you filled in, and we carried a whole machine — PedroTuningStore,
 * TuningRecorder — to flatten those nested objects into the per-robot tuning file. Pedro 3 replaces
 * them with a validated config built once from a lambda, so that machine is gone and the values
 * below are PLAIN PUBLIC STATIC DOUBLES. That is the whole simplification:
 *
 *   - Panels shows them (this class is @Configurable), so they are Tier-1 live tunables (§6).
 *   - Persistence saves and loads them like any other tunable, because they are flat primitives.
 *     Constants is registered in Persistence.TUNING_CLASSES — no bespoke key table any more.
 *   - The canonical values stay the COMMITTED per-robot JSON in tuning/ (§6). The numbers here are
 *     fallback defaults for a fresh hub.
 *
 * WHAT IS LIVE, AND WHAT NEEDS AN OPMODE RESTART:
 *   - The FEEDBACK GAINS are live. They are wired into Pedro through {@code Controller} suppliers
 *     (see {@link #foresightFor}), so the controller asks this class for the number every time it
 *     runs. Turn a gain in Panels and the robot changes on the next loop.
 *   - Everything else — velocities, decelerations, brake coefficients, pod offsets, max power — is
 *     read ONCE when the follower is built. Change one, then re-init the OpMode.
 *
 * HOW TO TUNE: run Pedro's AutoTune (the "Pedro Tuning" OpMode opens a web page on the robot). It
 * measures the values and prints a paste-ready block. Put its numbers in the matching fields below
 * for the robot you tuned, or turn them in Panels and commit that robot's tuning file.
 * ---------------------------------------------------------------------------------------------
 *
 * UNTUNED ROBOTS DRIVE AT HALF POWER. Foresight needs brake coefficients that only AutoTune can
 * measure, and we do not have them yet for either robot. Until a robot's {@code ...PedroTuned} flag
 * below is set true, {@link #createFollower} caps it at {@link #untunedMaxPower} and says so loudly.
 * That is the same fail-closed reasoning the UNKNOWN hub already used (§5, §6).
 */
@Configurable
public class Constants {

    // ===================================================================================
    // SHARED — the wiring is identical on both robots (CLAUDE.md §10)
    // ===================================================================================

    /** Power ceiling for a robot whose Pedro tuning has not been measured yet. */
    public static double untunedMaxPower = 0.5;

    // ===================================================================================
    // COMPETITION ROBOT
    // ===================================================================================

    /** Set true only after AutoTune has been run ON the comp robot and its numbers are below. */
    public static boolean compPedroTuned = false;

    /** Max forward velocity, in/s. Measured 2026-07-18 under Pedro 2; carries over unchanged. */
    public static double compForwardVelocity = 81.34056;
    /** Max lateral velocity, in/s. Measured 2026-07-18 under Pedro 2; carries over unchanged. */
    public static double compStrafeVelocity = 65.43028;
    /** Power ceiling once tuned, 0..1. */
    public static double compMaxPower = 1.0;

    /** Pinpoint pod offsets, inches. Measured on-robot 2026-07-18 (§10). */
    public static double compForwardPodY = 6.735;
    public static double compStrafePodX = 0.287;

    // Foresight — ALL UNTUNED PLACEHOLDERS. AutoTune replaces every number in this block.
    public static double compTranslationalForwardPrimary = 0.1;
    public static double compTranslationalForwardSecondary = 0.02;
    public static double compTranslationalStrafePrimary = 0.1;
    public static double compTranslationalStrafeSecondary = 0.02;
    public static double compHeadingP = 1.0;
    public static double compCoastFF = 0.01;
    public static double compBrakeFF = 0.01;
    public static double compBrakeLinearForward = 0.01;
    public static double compBrakeLinearStrafe = 0.01;
    public static double compBrakeQuadraticForward = 0.001;
    public static double compBrakeQuadraticStrafe = 0.001;
    public static double compHeadingBrakeLinear = 0.01;
    public static double compHeadingBrakeQuadratic = 0.001;
    public static double compForwardDeceleration = 60.0;
    public static double compStrafeDeceleration = 60.0;

    // ===================================================================================
    // TEST BOT
    // ===================================================================================

    /** Set true only after AutoTune has been run ON the test bot and its numbers are below. */
    public static boolean testPedroTuned = false;

    /** Max forward velocity, in/s. Measured under Pedro 2; carries over unchanged. */
    public static double testForwardVelocity = 78.27354;
    /** Max lateral velocity, in/s. Measured under Pedro 2; carries over unchanged. */
    public static double testStrafeVelocity = 61.582;
    public static double testMaxPower = 1.0;

    /**
     * Test-bot Pinpoint pod offsets, inches.
     * The strafe sign was corrected 2026-09-04 (was -2.1985): positive means the strafe pod sits
     * FORWARD of the tracking center. A wrong sign shows up only when the robot turns, as error that
     * grows with every rotation.
     */
    public static double testForwardPodY = 4.3823;
    public static double testStrafePodX = 2.1985;

    // Foresight — ALL UNTUNED PLACEHOLDERS, same as comp.
    public static double testTranslationalForwardPrimary = 0.1;
    public static double testTranslationalForwardSecondary = 0.02;
    public static double testTranslationalStrafePrimary = 0.1;
    public static double testTranslationalStrafeSecondary = 0.02;
    public static double testHeadingP = 1.0;
    public static double testCoastFF = 0.01;
    public static double testBrakeFF = 0.01;
    public static double testBrakeLinearForward = 0.01;
    public static double testBrakeLinearStrafe = 0.01;
    public static double testBrakeQuadraticForward = 0.001;
    public static double testBrakeQuadraticStrafe = 0.001;
    public static double testHeadingBrakeLinear = 0.01;
    public static double testHeadingBrakeQuadratic = 0.001;
    public static double testForwardDeceleration = 60.0;
    public static double testStrafeDeceleration = 60.0;

    // ===================================================================================
    // UNKNOWN HUB — fail closed (CLAUDE.md §5, §6)
    // ===================================================================================
    //
    // A hub whose network name is neither robot's. We must still build a follower — the OpMode
    // cannot run without one — but we must never hand it comp's tuning. It gets its own untuned
    // numbers, always at untunedMaxPower, and createFollower logs a warning naming the problem.

    public static double fallbackForwardVelocity = 60.0;
    public static double fallbackStrafeVelocity = 45.0;
    public static double fallbackForwardPodY = 0.0;
    public static double fallbackStrafePodX = 0.0;

    // ===================================================================================
    // Builder
    // ===================================================================================

    /**
     * Builds the follower for the robot we are actually running on.
     *
     * Pedro 3 builds a follower from three pieces: a localizer (where are we), a drivetrain (how do
     * we move), and an algorithm (how do we follow a path). Foresight is the algorithm.
     *
     * @param hardwareMap the OpMode's hardware map
     * @param id          resolved once at init by {@link RobotIdentity#resolve()} — pass it in rather
     *                    than resolving here, so one OpMode reads the hub name exactly once
     */
    public static Follower createFollower(HardwareMap hardwareMap, RobotIdentity id) {
        Localizer localizer = localizerFor(hardwareMap);
        Drivetrain drivetrain = drivetrainFor(hardwareMap);
        Foresight algorithm = new Foresight(foresightFor(id));

        if (!id.isKnown()) {
            RobotLog.ww("PedroConstants", "UNKNOWN robot (name=\"%s\") — untuned fallback Pedro "
                            + "config at %.2f max power. Path following will be inaccurate.",
                    id.networkName, untunedMaxPower);
        } else if (!isTuned(id)) {
            RobotLog.ww("PedroConstants", "%s has NOT been tuned for Pedro 3 — running at %.2f max "
                            + "power on placeholder brake coefficients. Run AutoTune, then set "
                            + "%sPedroTuned = true.",
                    id.robot, untunedMaxPower, id.robot == RobotIdentity.Robot.COMPETITION ? "comp" : "test");
        }

        // Read the values back OUT of what we just built, not out of the statics we wrote. A value
        // that persists but never reaches the follower looks perfectly tuned everywhere else; this
        // log is the only thing that would catch it (CLAUDE.md §6).
        RobotLog.ii("PedroConstants", "follower built for %s: maxPower=%.2f fwdVel=%.3f "
                        + "strafeVel=%.3f forwardPodY=%.4f strafePodX=%.4f tuned=%s",
                id.robot, maxPowerFor(id), forwardVelocityFor(id), strafeVelocityFor(id),
                forwardPodYFor(id), strafePodXFor(id), isTuned(id));

        return new Follower(localizer, drivetrain, algorithm);
    }

    /**
     * This robot's localizer. Public because AutoTune's procedures build their own — they need the
     * same hardware this file describes, and there must be exactly one description of it.
     */
    public static Localizer localizerFor(HardwareMap hardwareMap) {
        return new PinpointLocalizer(hardwareMap, pinpointFor(RobotIdentity.resolve()));
    }

    /** This robot's drivetrain. Public for the same reason as {@link #localizerFor}. */
    public static Drivetrain drivetrainFor(HardwareMap hardwareMap) {
        return new Mecanum(hardwareMap, mecanumFor(RobotIdentity.resolve()));
    }

    // ===================================================================================
    // Per-robot value selection
    // ===================================================================================

    /** True once AutoTune has been run on this robot and its numbers are in this file. */
    public static boolean isTuned(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compPedroTuned;
            case TESTBOT:     return testPedroTuned;
            default:          return false; // an UNKNOWN hub is never "tuned"
        }
    }

    /** Power ceiling for this robot. An untuned or unidentified robot is always capped. */
    public static double maxPowerFor(RobotIdentity id) {
        if (!isTuned(id)) return untunedMaxPower;
        return id.robot == RobotIdentity.Robot.COMPETITION ? compMaxPower : testMaxPower;
    }

    public static double forwardVelocityFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compForwardVelocity;
            case TESTBOT:     return testForwardVelocity;
            default:          return fallbackForwardVelocity;
        }
    }

    public static double strafeVelocityFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compStrafeVelocity;
            case TESTBOT:     return testStrafeVelocity;
            default:          return fallbackStrafeVelocity;
        }
    }

    public static double forwardPodYFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compForwardPodY;
            case TESTBOT:     return testForwardPodY;
            default:          return fallbackForwardPodY;
        }
    }

    public static double strafePodXFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compStrafePodX;
            case TESTBOT:     return testStrafePodX;
            default:          return fallbackStrafePodX;
        }
    }

    // ===================================================================================
    // Config builders — shared wiring in ONE place so the two robots cannot drift apart
    // ===================================================================================

    /**
     * Motor names and directions, identical on both robots (§10), plus nothing tunable. Writing the
     * wiring twice would let a fix land on one robot and not the other.
     */
    private static MecanumConfig mecanumFor(RobotIdentity id) {
        return new MecanumConfig(c -> {
            c.frontLeftName.set("LF_Motor");
            c.backLeftName.set("LR_Motor");
            c.frontRightName.set("RF_Motor");
            c.backRightName.set("RR_Motor");
            c.frontLeftDirection.set(DcMotorSimple.Direction.REVERSE);
            c.backLeftDirection.set(DcMotorSimple.Direction.REVERSE);
            c.frontRightDirection.set(DcMotorSimple.Direction.FORWARD);
            c.backRightDirection.set(DcMotorSimple.Direction.FORWARD);
        });
    }

    /**
     * Pinpoint device config and this robot's pod offsets.
     *
     * OFFSET MAPPING, verified against both versions' sources rather than assumed: Pedro 2 called
     * {@code setOffsets(forwardPodY, strafePodX, unit)} and Pedro 3 calls
     * {@code setOffsets(xPodOffset, yPodOffset, unit)} — the same driver call in the same order. So
     * xPodOffset IS our forwardPodY and yPodOffset IS our strafePodX.
     *
     * TODO: confirm the two encoder directions with AutoTune's Pinpoint tuner. Pedro 2 left them at
     * its own defaults, so FORWARD/FORWARD reproduces today's behavior but is not measured.
     */
    private static PinpointConfig pinpointFor(RobotIdentity id) {
        return new PinpointConfig(c -> {
            c.name.set("pinpoint");
            c.podType.set(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
            c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.xPodOffset.set(forwardPodYFor(id));
            c.yPodOffset.set(strafePodXFor(id));
        });
    }

    /**
     * Foresight, Pedro 3's path-following algorithm.
     *
     * WHY THE GAINS ARE PASSED AS SUPPLIERS ({@code () -> ...}) AND NOT AS PLAIN NUMBERS: a plain
     * number is read once, here, when the follower is built — turning it in Panels afterwards would
     * do nothing until the next OpMode restart. A supplier is asked for the value every time the
     * controller runs, so a gain you turn on the bench changes the robot on the very next loop.
     * That is what keeps these Tier-1 live tunables (§6). Values that Pedro reads once at build time
     * — velocities, decelerations, brake coefficients — cannot work that way, and the class comment
     * says which is which.
     */
    public static ForesightConfig foresightFor(RobotIdentity id) {
        boolean comp = id.robot == RobotIdentity.Robot.COMPETITION;
        boolean known = id.isKnown();

        return new ForesightConfig(c -> {
            // Piecewise: the secondary (gentler) gain far from the path, the primary gain within
            // 2.5 inches of it. Pedro's own tuner emits this shape.
            c.forwardTranslational.set(Controller.piecewise(
                            Controller.proportional(() -> known
                                    ? (comp ? compTranslationalForwardSecondary : testTranslationalForwardSecondary)
                                    : compTranslationalForwardSecondary))
                    .put(2.5, Controller.proportional(() -> known
                            ? (comp ? compTranslationalForwardPrimary : testTranslationalForwardPrimary)
                            : compTranslationalForwardPrimary)));

            c.strafeTranslational.set(Controller.piecewise(
                            Controller.proportional(() -> known
                                    ? (comp ? compTranslationalStrafeSecondary : testTranslationalStrafeSecondary)
                                    : compTranslationalStrafeSecondary))
                    .put(2.5, Controller.proportional(() -> known
                            ? (comp ? compTranslationalStrafePrimary : testTranslationalStrafePrimary)
                            : compTranslationalStrafePrimary)));

            c.headingFeedback.set(Controller.proportional(
                    () -> comp || !known ? compHeadingP : testHeadingP));

            c.coast.set(Controller.proportionalFeedforward(
                    () -> comp || !known ? compCoastFF : testCoastFF));
            c.brake.set(Controller.proportionalFeedforward(
                    () -> comp || !known ? compBrakeFF : testBrakeFF));

            // Read once at build time — see the note above.
            c.headingBrakeCoefficients.set(Vector2D.cartesian(
                    comp || !known ? compHeadingBrakeLinear : testHeadingBrakeLinear,
                    comp || !known ? compHeadingBrakeQuadratic : testHeadingBrakeQuadratic));
            c.linearBrakeCoefficients.set(Matrix.diag(
                    comp || !known ? compBrakeLinearForward : testBrakeLinearForward,
                    comp || !known ? compBrakeLinearStrafe : testBrakeLinearStrafe));
            c.quadraticBrakeCoefficients.set(Matrix.diag(
                    comp || !known ? compBrakeQuadraticForward : testBrakeQuadraticForward,
                    comp || !known ? compBrakeQuadraticStrafe : testBrakeQuadraticStrafe));

            c.maxAchievableForwardVelocity.set(forwardVelocityFor(id));
            c.maxAchievableStrafeVelocity.set(strafeVelocityFor(id));
            c.naturalForwardDeceleration.set(comp || !known ? compForwardDeceleration : testForwardDeceleration);
            c.naturalStrafeDeceleration.set(comp || !known ? compStrafeDeceleration : testStrafeDeceleration);

            c.maxPathSpeed.set(maxPowerFor(id));
        });
    }
}
