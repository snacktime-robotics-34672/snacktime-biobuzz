package org.firstinspires.ftc.teamcode.pedroPathing;

import com.bylazar.configurables.annotations.Configurable;
import com.pedropathing.control.FilteredPIDFCoefficients;
import com.pedropathing.control.PIDFCoefficients;
import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.FollowerBuilder;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;
import com.pedropathing.ftc.localization.localizers.PinpointLocalizer;
import com.pedropathing.localization.Localizer;
import com.pedropathing.geometry.Pose;
import com.pedropathing.math.Vector;
import com.pedropathing.paths.PathConstraints;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.util.Persistence;
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
 *   2. Turn the knob in Panels. About a second after you stop, the value is written to that robot's
 *      tuning file on the hub, and a paste-ready block goes to the RC log under PEDRO_TUNED.
 *   3. Pull the file with ./save-tuning.sh and COMMIT IT. git is the backup (CLAUDE.md §12).
 *      Committing the file is the save — do not transcribe numbers into the sets below.
 *
 * WHAT IS SHARED: motor names and directions (the wiring is identical on both robots, CLAUDE.md §10)
 * and {@link #pathConstraints}. Everything else is split.
 *
 * WHY @Configurable: without it, Panels never shows these values, so the PIDF tuners in
 * {@link Tuning} have no knobs to turn — you can run Translational/Heading/Drive Tuner and watch the
 * robot, but you cannot change a gain while it runs. Panels finds tunables by scanning for this
 * annotation on the class, then walks the object graph below each public static field. So annotating
 * this one class exposes every nested gain (e.g. compFollowerConstants → coefficientsTranslationalPIDF
 * → P/I/D/F) as a live dashboard field.
 *
 * WHAT IS LIVE vs. WHAT NEEDS A RESTART (verified against Pedro 2.1.2 source):
 *   - FollowerConstants (PIDF gains, mass, zero-power accelerations, centripetal scaling) — LIVE.
 *     Pedro's VectorCalculator re-reads the whole FollowerConstants object every loop, so a value you
 *     change in Panels takes effect on the very next cycle. This is the set you actually tune.
 *   - MecanumConstants xVelocity / yVelocity / maxPower — LIVE, but only because {@link #applyLive}
 *     pushes them in each loop. Motor names and directions are wiring, not tuning; leave them alone.
 *   - frontLeftVector — NOT live. The drivetrain bakes it into its own wheel vectors when it is
 *     built, so re-init the OpMode after you change the drive velocities.
 *   - PinpointConstants pod offsets — LIVE as of 2026-09-04, and only because {@link #applyLive}
 *     writes them to the Pinpoint when they change. Pedro itself reads them once, in the localizer's
 *     constructor, so before this you had to re-init to see any effect — and the re-init reloaded the
 *     tuning file over your edit, which is how a measured offset got lost. Everything else on
 *     PinpointConstants (encoder resolution, encoder directions, yaw scalar) is still read once at
 *     build time and still needs a re-init.
 *
 * A running OpMode only picks up live edits if it calls {@link #applyLive} each loop. The Tuning
 * suite does; match OpModes deliberately do not, because that is per-loop cost for a knob nobody
 * turns mid-match (CLAUDE.md §0).
 *
 * Panels shows all three sets (comp / test / fallback). Turn the knobs for the robot you are ON —
 * the Tuning banner names it. Editing comp's gains while standing at the test bot changes nothing.
 *
 * CHANGED 2026-09-01 — THESE NOW SAVE AND LOAD THEMSELVES (CLAUDE.md §6). They used to be code-only,
 * which meant a tuning session lived in RAM until someone transcribed it by hand, and a power cycle
 * or a crashed OpMode lost the lot. {@link PedroTuningStore} now flattens them into "Pedro.*" keys in
 * the same per-robot tuning file as every other tunable; {@link TuningRecorder} saves them
 * automatically when a value settles, and {@link #createFollower} loads them before it builds.
 *
 * So the VALUES IN THIS FILE ARE NOW FALLBACK DEFAULTS, not the canonical tuning — the committed
 * per-robot JSON is. Editing a number here changes what a robot does only when its file is missing,
 * rejected, or TuningConfig.pedroTuningLoadEnabled is false.
 *
 * Still do NOT add Constants.class to Persistence.TUNING_CLASSES — that path reflects over public
 * static fields and cannot see inside Pedro's nested coefficient objects. PedroTuningStore's explicit
 * table is what handles these, and it is round-trip unit-tested.
 */
@Configurable
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
    public static MecanumConstants testMecanumConstants = mecanumFor(78.27354, 61.582, 1.0);

    /**
     * Test-bot Pinpoint pod offsets. See the note on {@link #compPinpointConstants}.
     *
     * CORRECTED 2026-09-04: strafePodX was -2.1985 and is now +2.1985. Positive means the strafe pod
     * sits FORWARD of the tracking center, so the old sign put it 2.2 inches behind when it is 2.2
     * inches ahead. A wrong pod offset shows up only when the robot turns, as position error that
     * grows with every rotation — which is the drift we were chasing. Where -2.1985 came from is not
     * recorded: it arrived with the two-robot split, not from a run of OffsetsTuner.
     */
    public static PinpointConstants testPinpointConstants = pinpointFor(4.3823, 2.1985);

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
        // Load this robot's saved Pedro tuning FIRST, before anything below reads a constant.
        //
        // WHY HERE and not at OpMode init: the follower is built once and captures these objects,
        // so a load that runs after the build silently does nothing. Putting the load inside the
        // one function that does the build means the order cannot be wrong — there is no rule for
        // a future OpMode to forget. Fail-closed and all-or-nothing; see PedroTuningStore.
        if (TuningConfig.pedroTuningLoadEnabled) {
            PedroTuningStore.applyFrom(Persistence.readTuningMap(id), id);
        } else {
            RobotLog.ww("PedroConstants",
                    "pedroTuningLoadEnabled=false — ignoring the saved tuning file, running on the "
                            + "in-code constants in Constants.java");
        }

        FollowerConstants follower = followerConstantsFor(id);
        MecanumConstants drivetrain = mecanumConstantsFor(id);
        PinpointConstants localizer = pinpointConstantsFor(id);

        if (!id.isKnown()) {
            RobotLog.ww("PedroConstants", "UNKNOWN robot (name=\"%s\") — using untuned fallback "
                    + "Pedro constants at %.2f max power. Path following will be inaccurate.",
                    id.networkName, drivetrain.maxPower);
        }

        RobotLog.ii("PedroConstants", "follower built for %s: mass=%.2f kg, xVel=%.3f, yVel=%.3f, "
                        + "forwardPodY=%.3f, strafePodX=%.3f",
                id.robot, follower.mass, drivetrain.xVelocity, drivetrain.yVelocity,
                localizer.forwardPodY, localizer.strafePodX);

        Follower built = new FollowerBuilder(follower, hardwareMap)
                .mecanumDrivetrain(drivetrain)
                .pinpointLocalizer(localizer)
                .pathConstraints(pathConstraints)
                .build();

        // Make maxPower real. The drivetrain copies it once when it is built, but followPath() then
        // resets the drivetrain back to the follower's own globalMaxPower — which is 1 until someone
        // sets it. That is how an UNKNOWN hub's half-power cap silently disappears the first time a
        // path runs. setMaxPower() writes both, so the cap holds.
        built.setMaxPower(drivetrain.maxPower);
        lastAppliedMaxPower = drivetrain.maxPower;

        // Log what the follower is ACTUALLY holding, read back out of the built object rather than
        // out of the statics we just wrote. A value that persists but never reaches the follower
        // looks perfectly tuned everywhere else; this is the only line that would catch it.
        PedroTuningStore.logAsBuilt(built.getConstants(), drivetrain, localizer);

        return built;
    }

    /** This robot's follower tuning set. Read fresh on every call, so a Panels edit is never cached. */
    public static FollowerConstants followerConstantsFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compFollowerConstants;
            case TESTBOT:     return testFollowerConstants;
            default:          return fallbackFollowerConstants;
        }
    }

    /** This robot's drive velocities and power cap. */
    public static MecanumConstants mecanumConstantsFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compMecanumConstants;
            case TESTBOT:     return testMecanumConstants;
            default:          return fallbackMecanumConstants;
        }
    }

    /** This robot's Pinpoint pod offsets. */
    public static PinpointConstants pinpointConstantsFor(RobotIdentity id) {
        switch (id.robot) {
            case COMPETITION: return compPinpointConstants;
            case TESTBOT:     return testPinpointConstants;
            default:          return fallbackPinpointConstants;
        }
    }

    // ===================================================================================
    // Live tuning — push Panels edits into a follower that is already running
    // ===================================================================================

    /**
     * The maxPower {@link #applyLive} last pushed into the follower. Reset whenever a follower is
     * built. It exists so applyLive() writes maxPower only when you actually turn that knob: a blind
     * write every loop would stomp on a per-path max power that followPath() had set.
     */
    private static double lastAppliedMaxPower = Double.NaN;

    // Pod offsets last written to the Pinpoint. NaN so the first comparison always differs and
    // the offsets get pushed once at the start of a session.
    private static double lastAppliedForwardPodY = Double.NaN;
    private static double lastAppliedStrafePodX = Double.NaN;

    /**
     * Pushes this robot's constants into a follower that is already running, so a number typed in
     * Panels changes how the robot drives on the next loop instead of at the next OpMode restart.
     * Call it once per loop from a tuning OpMode.
     *
     * This is a bench tool. It costs a handful of field reads and writes per loop — fine while
     * tuning, but it does not belong in a match loop (CLAUDE.md §0).
     *
     * WHY MOST OF IT IS ALREADY A NO-OP (checked against Pedro 2.1.2 and Panels configurables
     * 1.0.5): Panels writes a value by reflection, straight into the object that owns the field. It
     * never builds a replacement object. Pedro re-reads its whole FollowerConstants every loop
     * through the reference it captured when the follower was built. Same object on both sides, so
     * a PIDF gain or a mass already reaches the robot on its own. What this method adds is the
     * parts where that is not true:
     *
     *   - maxPower was never applied at all. See the note in {@link #createFollower}.
     *   - Drive velocities are pushed BY VALUE, not by swapping a reference, because the drivetrain
     *     gives us no way to re-point it at a different MecanumConstants object. Writing through the
     *     follower lands on whatever object the drivetrain is actually reading.
     *   - The references themselves. Nothing in Panels replaces an object today, but a hot reload
     *     can, and that failure is silent: the dashboard shows the new object while the robot keeps
     *     driving on the old one. The identity checks below cost nothing and close that door.
     *
     * STILL NOT LIVE, by construction — change these and re-init the OpMode:
     *   - Pinpoint pod offsets. The localizer reads them once, when it is built.
     *   - frontLeftVector. The drivetrain bakes it into its own wheel vectors at construction, so a
     *     new xVelocity/yVelocity pair does not reshape it. Re-init after tuning the velocities.
     */
    public static void applyLive(Follower follower, RobotIdentity id) {
        FollowerConstants live = followerConstantsFor(id);
        MecanumConstants drive = mecanumConstantsFor(id);

        if (follower.getConstants() != live) follower.setConstants(live);
        if (follower.getConstraints() != pathConstraints) follower.setConstraints(pathConstraints);

        follower.setXVelocity(drive.xVelocity);
        follower.setYVelocity(drive.yVelocity);

        if (drive.maxPower != lastAppliedMaxPower) {
            follower.setMaxPower(drive.maxPower);
            lastAppliedMaxPower = drive.maxPower;
        }

        applyPodOffsets(follower, pinpointConstantsFor(id));
    }

    /**
     * Writes the pod offsets to the Pinpoint, but ONLY when they change.
     *
     * WHY ONLY ON CHANGE: this is an I2C write, and I2C is the most expensive thing on the bus
     * (CLAUDE.md §4 rule 5). Comparing two doubles costs nothing; writing every loop would spend
     * real loop time to tell the device something it already knows. In a normal match the values
     * never change, so this costs two compares and never writes at all.
     *
     * ARGUMENT ORDER IS NOT A TYPO. goBILDA calls the forward-measuring pod the "X pod" and the
     * strafe-measuring pod the "Y pod", and its setOffsets takes the X pod's SIDEWAYS position
     * first, then the Y pod's FORWARD position. Pedro names those same two numbers forwardPodY and
     * strafePodX. So forwardPodY goes in first. This mirrors what PinpointLocalizer's own
     * constructor does, and the two must agree or a re-init would move the robot's idea of its pods.
     */
    private static void applyPodOffsets(Follower follower, PinpointConstants p) {
        if (p.forwardPodY == lastAppliedForwardPodY && p.strafePodX == lastAppliedStrafePodX) return;

        Localizer localizer = follower.getPoseTracker().getLocalizer();
        if (!(localizer instanceof PinpointLocalizer)) return; // nothing to write to

        ((PinpointLocalizer) localizer).getPinpoint()
                .setOffsets(p.forwardPodY, p.strafePodX, p.distanceUnit);
        lastAppliedForwardPodY = p.forwardPodY;
        lastAppliedStrafePodX = p.strafePodX;
        RobotLog.i("Constants: pod offsets pushed live → forwardPodY=%.4f strafePodX=%.4f",
                p.forwardPodY, p.strafePodX);
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
                .strafePodX(strafePodX)
                .distanceUnit(DistanceUnit.INCH);
    }
}