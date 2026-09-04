package org.firstinspires.ftc.teamcode.pedroPathing;

import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.at;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.changes;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.drawCurrent;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.drawCurrentAndHistory;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.follower;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.startCentered;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.stopRobot;
import static org.firstinspires.ftc.teamcode.pedroPathing.Tuning.telemetryM;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.configurables.annotations.IgnoreConfigurable;
import com.bylazar.field.FieldManager;
import com.bylazar.field.PanelsField;
import com.bylazar.field.Style;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.pedropathing.follower.Follower;
import com.pedropathing.geometry.*;
import com.pedropathing.math.*;
import com.pedropathing.paths.*;
import com.pedropathing.telemetry.SelectableOpMode;
import com.pedropathing.util.PoseHistory;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.util.RobotIdentity;
import org.firstinspires.ftc.teamcode.util.TunableWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the Tuning class. It contains a selection menu for various tuning OpModes.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 6/26/2025
 */
@Configurable
@TeleOp(name = "Tuning", group = "Pedro Pathing")
public class Tuning extends SelectableOpMode {
    // @IgnoreConfigurable is REQUIRED here, not tidiness. Panels walks every public static field on
    // an @Configurable class and recurses into whatever it finds. A Follower is not a tunable value —
    // it is the whole runtime object graph (localizer, drivetrain, VectorCalculator, every
    // PIDFController, paths, pose history). Letting Panels descend into it registered ~4000 fields and
    // crowded out the classes we actually tune: Constants never made it into the registry, so every
    // edit to a Pedro PIDF was looked up, not found, and dropped WITHOUT any error in the dashboard.
    // The values still rendered, which is what made this so hard to see. Keep this annotation.
    @IgnoreConfigurable
    public static Follower follower;

    @IgnoreConfigurable
    static PoseHistory poseHistory;

    @IgnoreConfigurable
    static TelemetryManager telemetryM;

    @IgnoreConfigurable
    static ArrayList<String> changes = new ArrayList<>();

    // Which robot is this? None of the ~15 tuning OpModes below had any robot-identity visibility
    // before — this is the one place (drawCurrent(), called by nearly all of them) that fixes that
    // for the whole suite at once, rather than wiring each OpMode individually.
    @IgnoreConfigurable
    static String idBanner;

    // Kept so drawCurrent() knows which robot's constant set to push into the follower each loop.
    // Resolved once in onSelect(), never re-read — the hub name cannot change while an OpMode runs.
    @IgnoreConfigurable
    static RobotIdentity robotId;

    /**
     * Where the path-following tuners start, and so where Panels draws them.
     *
     * Pedro's origin (0,0) is the bottom-left FIELD CORNER, not the center. A path anchored there
     * runs along the wall, and Panels draws half the robot off the canvas, which makes the trace
     * hard to read. Field center gives every test clear room on all sides.
     *
     * This is deliberately NOT the whole suite's start pose. Four tuners read the raw pose as their
     * measurement: ForwardTuner and LateralTuner report getX()/getY() as "Distance Moved", and the
     * two velocity tuners stop once getX()/getY() passes DISTANCE. Starting those anywhere but the
     * origin would report a wrong multiplier, or end the run on the first loop. So onSelect() keeps
     * the origin, and only the tuners that follow a path opt in through startCentered().
     *
     * OffsetsTuner already assumed this pose — its math is (72 - x) / 2 — so it now shares this one.
     */
    @IgnoreConfigurable
    static final Pose START = new Pose(72, 72, 0);

    /** A pose measured from {@link #START}: x forward, y left, in inches. */
    static Pose at(double x, double y) {
        return new Pose(START.getX() + x, START.getY() + y);
    }

    /** Same as {@link #at(double, double)}, carrying a heading in radians. */
    static Pose at(double x, double y, double heading) {
        return new Pose(START.getX() + x, START.getY() + y, heading);
    }

    /**
     * Anchors a path-following tuner at field center. Call it from init(), so the robot is already
     * drawn in the right place while you read the init_loop() instructions.
     *
     * The paths must be built with at() as well. The tuner paths are absolute field coordinates, so
     * moving the pose alone would leave the path in the corner and the follower would drive the
     * robot 100 inches across the field to reach it.
     */
    static void startCentered() {
        follower.setStartingPose(START);
    }

    public Tuning() {
        super("Select a Tuning OpMode", s -> {
            s.folder("Localization", l -> {
                l.add("Localization Test", LocalizationTest::new);
                l.add("Offsets Tuner", OffsetsTuner::new);
                l.add("Forward Tuner", ForwardTuner::new);
                l.add("Lateral Tuner", LateralTuner::new);
                l.add("Turn Tuner", TurnTuner::new);
            });
            s.folder("Automatic", a -> {
                a.add("Forward Velocity Tuner", ForwardVelocityTuner::new);
                a.add("Lateral Velocity Tuner", LateralVelocityTuner::new);
                a.add("Forward Zero Power Acceleration Tuner", ForwardZeroPowerAccelerationTuner::new);
                a.add("Lateral Zero Power Acceleration Tuner", LateralZeroPowerAccelerationTuner::new);
            });
            s.folder("Manual", p -> {
                p.add("Translational Tuner", TranslationalTuner::new);
                p.add("Heading Tuner", HeadingTuner::new);
                p.add("Drive Tuner", DriveTuner::new);
                p.add("Centripetal Tuner", CentripetalTuner::new);
            });
            s.folder("Tests", p -> {
                p.add("Line", Line::new);
                p.add("Triangle", Triangle::new);
                p.add("Circle", Circle::new);
            });
        });
    }

    @Override
    public void onSelect() {
        // Resolved once per OpMode selection (init-time, not the loop — §4), then used twice: it
        // picks which robot's Pedro constants the follower is built from, and it feeds the banner
        // that drawCurrent() emits every loop across the whole tuning suite.
        RobotIdentity id = RobotIdentity.resolve();
        robotId = id;
        idBanner = id.banner();

        follower = Constants.createFollower(hardwareMap, id);
        TuningRecorder.reset();
        TunableWatcher.reset();
        telemetry.addLine(PedroTuningStore.lastStatus());

        // No refreshClass() call here on purpose. The Sloth build of Panels registers a class when
        // Sloth loads it, so Constants is already registered against this exact classloader by the
        // time we get here. A refresh would only rebuild the dashboard's field ids, which orphans
        // the ids an already-open browser tab is holding — turning edits back into silent no-ops,
        // the very failure this all started with.

        follower.setStartingPose(new Pose());

        poseHistory = follower.getPoseHistory();

        telemetryM = PanelsTelemetry.INSTANCE.getTelemetry();
        Drawing.init();
    }

    @Override
    public void onLog(List<String> lines) {}

    /**
     * Pushes Panels edits into the running follower, then draws the robot.
     *
     * WHY THE APPLY LIVES HERE: {@code SelectableOpMode.loop()} is final, so Tuning cannot wrap the
     * selected tuner's loop and do this in one obvious place. drawCurrent() is the one call every
     * tuner in this file already makes each loop, in both init_loop() and loop(), so it is the only
     * seam that reaches the whole suite at once. Same reason the identity banner lives here.
     */
    public static void drawCurrent() {
        // First, before anything below reads the follower. Outside the try/catch on purpose: a
        // failure to apply tuning is not a drawing failure and must not be reported as one.
        Constants.applyLive(follower, robotId);

        // Record tuned values to the RC log so a session cannot be lost to a power cycle. Cheap on
        // the steady path (a few double compares, no allocation) and it only writes when a value
        // you changed settles. See TuningRecorder for why this is on change and not on stop.
        long nowNanos = System.nanoTime();
        TuningRecorder.poll(robotId, nowNanos);
        // Everything that is NOT a Pedro constant — servo positions, speed caps, thresholds. Pedro
        // values are watched above; this catches a tunable you turn while a tuner happens to be the
        // OpMode running.
        TunableWatcher.poll(robotId, nowNanos);

        try {
            // Queued here (not flushed) — each sub-OpMode's own telemetryM.update(telemetry) call
            // does the actual send, same as every other debug() line in this file already works.
            // Whether that happens just before or after this call in a given OpMode only costs one
            // loop of display lag for a value that never changes mid-session (§4 — not loop-time-
            // sensitive; this is a Panels/DS debug line, not a hot-path read). Inside the same
            // try/catch as the drawing below now, so a null/unready telemetryM can't take down
            // drawing (they were previously independent; a failure in one shouldn't crash the other).
            telemetryM.debug(idBanner);
            Drawing.drawRobot(follower.getPose());
            Drawing.sendPacket();
        } catch (Exception e) {
            throw new RuntimeException("Drawing failed " + e);
        }
    }

    public static void drawCurrentAndHistory() {
        Drawing.drawPoseHistory(poseHistory);
        drawCurrent();
    }

    /** This creates a full stop of the robot by setting the drive motors to run at 0 power. */
    public static void stopRobot() {
        follower.startTeleopDrive(true);
        follower.setTeleOpDrive(0,0,0,true);
    }
}

/**
 * This is the LocalizationTest OpMode. This is basically just a simple mecanum drive attached to a
 * PoseUpdater. The OpMode will print out the robot's pose to telemetry as well as draw the robot.
 * You should use this to check the robot's localization.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 5/6/2024
 */
class LocalizationTest extends OpMode {
    @Override
    public void init() {}

    /** This initializes the PoseUpdater, the mecanum drive motors, and the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("This will print your robot's position to telemetry while "
                + "allowing robot control through a basic mecanum drive on gamepad 1.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.startTeleopDrive();
        follower.update();
    }

    /**
     * This updates the robot's pose estimate, the simple mecanum drive, and updates the
     * Panels telemetry with the robot's position as well as draws the robot's position.
     */
    @Override
    public void loop() {
        follower.setTeleOpDrive(-gamepad1.left_stick_y, -gamepad1.left_stick_x, -gamepad1.right_stick_x, true);
        follower.update();

        telemetryM.debug(String.format("x: %.3f", follower.getPose().getX()));
        telemetryM.debug(String.format("y: %.3f", follower.getPose().getY()));
        telemetryM.debug(String.format("heading: %.3f deg", Math.toDegrees(follower.getPose().getHeading())));
        telemetryM.debug(String.format("total heading: %.3f deg", Math.toDegrees(follower.getTotalHeading())));
        telemetryM.update(telemetry);

        drawCurrentAndHistory();
    }
}

/**
 * This is the ForwardTuner OpMode. This tracks the forward movement of the robot and displays the
 * necessary ticks to inches multiplier. This displayed multiplier is what's necessary to scale the
 * robot's current distance in ticks to the specified distance in inches. So, to use this, run the
 * tuner, then pull/push the robot to the specified distance using a ruler on the ground. When you're
 * at the end of the distance, record the ticks to inches multiplier. Feel free to run multiple trials
 * and average the results. Then, input the multiplier into the forward ticks to inches in your
 * localizer of choice.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 5/6/2024
 */
@Configurable
class ForwardTuner extends OpMode {
    public static double DISTANCE = 48;

    @Override
    public void init() {
        follower.update();
        drawCurrent();
    }

    /** This initializes the PoseUpdater as well as the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("Pull your robot forward " + DISTANCE + " inches. Your forward ticks to inches will be shown on the telemetry.");
        telemetryM.update(telemetry);
        drawCurrent();
    }

    /**
     * This updates the robot's pose estimate, and updates the Panels telemetry with the
     * calculated multiplier and draws the robot.
     */
    @Override
    public void loop() {
        follower.update();

        telemetryM.debug("Distance Moved: " + follower.getPose().getX());
        telemetryM.debug("The multiplier will display what your forward ticks to inches should be to scale your current distance to " + DISTANCE + " inches.");
        telemetryM.debug("Multiplier: " + (DISTANCE / (follower.getPose().getX() / follower.getPoseTracker().getLocalizer().getForwardMultiplier())));
        telemetryM.update(telemetry);

        drawCurrentAndHistory();
    }
}

/**
 * This is the LateralTuner OpMode. This tracks the strafe movement of the robot and displays the
 * necessary ticks to inches multiplier. This displayed multiplier is what's necessary to scale the
 * robot's current distance in ticks to the specified distance in inches. So, to use this, run the
 * tuner, then pull/push the robot to the specified distance using a ruler on the ground. When you're
 * at the end of the distance, record the ticks to inches multiplier. Feel free to run multiple trials
 * and average the results. Then, input the multiplier into the strafe ticks to inches in your
 * localizer of choice.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 2.0, 6/26/2025
 */
@Configurable
class LateralTuner extends OpMode {
    public static double DISTANCE = 48;

    @Override
    public void init() {
        follower.update();
        drawCurrent();
    }

    /** This initializes the PoseUpdater as well as the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("Pull your robot to the right " + DISTANCE + " inches. Your strafe ticks to inches will be shown on the telemetry.");
        telemetryM.update(telemetry);
        drawCurrent();
    }

    /**
     * This updates the robot's pose estimate, and updates the Panels telemetry with the
     * calculated multiplier and draws the robot.
     */
    @Override
    public void loop() {
        follower.update();

        telemetryM.debug("Distance Moved: " + follower.getPose().getY());
        telemetryM.debug("The multiplier will display what your strafe ticks to inches should be to scale your current distance to " + DISTANCE + " inches.");
        telemetryM.debug("Multiplier: " + (DISTANCE / (follower.getPose().getY() / follower.getPoseTracker().getLocalizer().getLateralMultiplier())));
        telemetryM.update(telemetry);

        drawCurrentAndHistory();
    }
}

/**
 * This is the TurnTuner OpMode. This tracks the turning movement of the robot and displays the
 * necessary ticks to inches multiplier. This displayed multiplier is what's necessary to scale the
 * robot's current angle in ticks to the specified angle in radians. So, to use this, run the
 * tuner, then pull/push the robot to the specified angle using a protractor or lines on the ground.
 * When you're at the end of the angle, record the ticks to inches multiplier. Feel free to run
 * multiple trials and average the results. Then, input the multiplier into the turning ticks to
 * radians in your localizer of choice.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 5/6/2024
 */
@Configurable
class TurnTuner extends OpMode {
    public static double ANGLE = 2 * Math.PI;

    @Override
    public void init() {
        follower.update();
        drawCurrent();
    }

    /** This initializes the PoseUpdater as well as the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("Turn your robot " + ANGLE + " radians. Your turn ticks to inches will be shown on the telemetry.");
        telemetryM.update(telemetry);

        drawCurrent();
    }

    /**
     * This updates the robot's pose estimate, and updates the Panels telemetry with the
     * calculated multiplier and draws the robot.
     */
    @Override
    public void loop() {
        follower.update();

        telemetryM.debug("Total Angle: " + follower.getTotalHeading());
        telemetryM.debug("The multiplier will display what your turn ticks to inches should be to scale your current angle to " + ANGLE + " radians.");
        telemetryM.debug("Multiplier: " + (ANGLE / (follower.getTotalHeading() / follower.getPoseTracker().getLocalizer().getTurningMultiplier())));
        telemetryM.update(telemetry);

        drawCurrentAndHistory();
    }
}

/**
 * This is the ForwardVelocityTuner autonomous follower OpMode. This runs the robot forwards at max
 * power until it reaches some specified distance. It records the most recent velocities, and on
 * reaching the end of the distance, it averages them and prints out the velocity obtained. It is
 * recommended to run this multiple times on a full battery to get the best results. What this does
 * is, when paired with StrafeVelocityTuner, allows FollowerConstants to create a Vector that
 * empirically represents the direction your mecanum wheels actually prefer to go in, allowing for
 * more accurate following.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 3/13/2024
 */
@Configurable
class ForwardVelocityTuner extends OpMode {
    private final ArrayList<Double> velocities = new ArrayList<>();
    public static double DISTANCE = 60;
    public static double RECORD_NUMBER = 10;

    private boolean end;

    @Override
    public void init() {}

    /** This initializes the drive motors as well as the cache of velocities and the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("The robot will run at 1 power until it reaches " + DISTANCE + " inches forward.");
        telemetryM.debug("Make sure you have enough room, since the robot has inertia after cutting power.");
        telemetryM.debug("After running the distance, the robot will cut power from the drivetrain and display the forward velocity.");
        telemetryM.debug("Press B on game pad 1 to stop.");
        telemetryM.debug("pose", follower.getPose());
        telemetryM.update(telemetry);

        follower.update();
        drawCurrent();
    }

    /** This starts the OpMode by setting the drive motors to run forward at full power. */
    @Override
    public void start() {
        for (int i = 0; i < RECORD_NUMBER; i++) {
            velocities.add(0.0);
        }
        follower.startTeleopDrive(true);
        follower.update();
        end = false;
    }

    /**
     * This runs the OpMode. At any point during the running of the OpMode, pressing B on
     * game pad 1 will stop the OpMode. This continuously records the RECORD_NUMBER most recent
     * velocities, and when the robot has run forward enough, these last velocities recorded are
     * averaged and printed.
     */
    @Override
    public void loop() {
        if (gamepad1.bWasPressed()) {
            stopRobot();
            requestOpModeStop();
        }

        follower.update();
        drawCurrentAndHistory();


        if (!end) {
            if (Math.abs(follower.getPose().getX()) > DISTANCE) {
                end = true;
                stopRobot();
            } else {
                follower.setTeleOpDrive(1,0,0,true);
                //double currentVelocity = Math.abs(follower.getVelocity().getXComponent());
                double currentVelocity = Math.abs(follower.poseTracker.getLocalizer().getVelocity().getX());
                velocities.add(currentVelocity);
                velocities.remove(0);
            }
        } else {
            stopRobot();
            double average = 0;
            for (double velocity : velocities) {
                average += velocity;
            }
            average /= velocities.size();
            telemetryM.debug("Forward Velocity: " + average);
            telemetryM.debug("\n");
            telemetryM.debug("Press A to set the Forward Velocity temporarily (while robot remains on).");
            telemetryM.debug("To keep it: record as xVelocity in THIS robot's mecanum set in Constants.java.");

            for (int i = 0; i < velocities.size(); i++) {
                telemetry.addData(String.valueOf(i), velocities.get(i));
            }

            telemetryM.update(telemetry);
            telemetry.update();

            if (gamepad1.aWasPressed()) {
                follower.setXVelocity(average);
                String message = "XMovement: " + average;
                changes.add(message);
            }
        }
    }
}

/**
 * This is the StrafeVelocityTuner autonomous follower OpMode. This runs the robot right at max
 * power until it reaches some specified distance. It records the most recent velocities, and on
 * reaching the end of the distance, it averages them and prints out the velocity obtained. It is
 * recommended to run this multiple times on a full battery to get the best results. What this does
 * is, when paired with ForwardVelocityTuner, allows FollowerConstants to create a Vector that
 * empirically represents the direction your mecanum wheels actually prefer to go in, allowing for
 * more accurate following.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 3/13/2024
 */
@Configurable
class LateralVelocityTuner extends OpMode {
    private final ArrayList<Double> velocities = new ArrayList<>();

    public static double DISTANCE = 60;
    public static double RECORD_NUMBER = 10;

    private boolean end;

    @Override
    public void init() {}

    /**
     * This initializes the drive motors as well as the cache of velocities and the Panels
     * telemetryM.
     */
    @Override
    public void init_loop() {
        telemetryM.debug("The robot will run at 1 power until it reaches " + DISTANCE + " inches to the robot right.");
        telemetryM.debug("Make sure you have enough room, since the robot has inertia after cutting power.");
        telemetryM.debug("After running the distance, the robot will cut power from the drivetrain and display the strafe velocity.");
        telemetryM.debug("Press B on Gamepad 1 to stop.");
        telemetryM.update(telemetry);

        follower.update();
        drawCurrent();
    }

    /** This starts the OpMode by setting the drive motors to run right at full power. */
    @Override
    public void start() {
        for (int i = 0; i < RECORD_NUMBER; i++) {
            velocities.add(0.0);
        }
        follower.startTeleopDrive(true);
        follower.update();
    }

    /**
     * This runs the OpMode. At any point during the running of the OpMode, pressing B on
     * game pad1 will stop the OpMode. This continuously records the RECORD_NUMBER most recent
     * velocities, and when the robot has run sideways enough, these last velocities recorded are
     * averaged and printed.
     */
    @Override
    public void loop() {
        if (gamepad1.bWasPressed()) {
            stopRobot();
            requestOpModeStop();
        }

        follower.update();
        drawCurrentAndHistory();

        if (!end) {
            if (Math.abs(follower.getPose().getY()) > DISTANCE) {
                end = true;
                stopRobot();
            } else {
                follower.setTeleOpDrive(0,1,0,true);
                double currentVelocity = Math.abs(follower.getVelocity().dot(new Vector(1, Math.PI / 2)));
                velocities.add(currentVelocity);
                velocities.remove(0);
            }
        } else {
            stopRobot();
            double average = 0;
            for (double velocity : velocities) {
                average += velocity;
            }
            average /= velocities.size();

            telemetryM.debug("Strafe Velocity: " + average);
            telemetryM.debug("\n");
            telemetryM.debug("Press A to set the Lateral Velocity temporarily (while robot remains on).");
            telemetryM.debug("To keep it: record as yVelocity in THIS robot's mecanum set in Constants.java.");
            telemetryM.update(telemetry);

            if (gamepad1.aWasPressed()) {
                follower.setYVelocity(average);
                String message = "YMovement: " + average;
                changes.add(message);
            }
        }
    }
}

/**
 * This is the ForwardZeroPowerAccelerationTuner autonomous follower OpMode. This runs the robot
 * forward until a specified velocity is achieved. Then, the robot cuts power to the motors, setting
 * them to zero power. The deceleration, or negative acceleration, is then measured until the robot
 * stops. The accelerations across the entire time the robot is slowing down is then averaged and
 * that number is then printed. This is used to determine how the robot will decelerate in the
 * forward direction when power is cut, making the estimations used in the calculations for the
 * drive Vector more accurate and giving better braking at the end of Paths.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/13/2024
 */
@Configurable
class ForwardZeroPowerAccelerationTuner extends OpMode {
    private final ArrayList<Double> accelerations = new ArrayList<>();
    public static double VELOCITY = 30;

    private double previousVelocity;
    private long previousTimeNano;

    private boolean stopping;
    private boolean end;

    @Override
    public void init() {}

    /** This initializes the drive motors as well as the Panels telemetryM. */
    @Override
    public void init_loop() {
        telemetryM.debug("The robot will run forward until it reaches " + VELOCITY + " inches per second.");
        telemetryM.debug("Then, it will cut power from the drivetrain and roll to a stop.");
        telemetryM.debug("Make sure you have enough room.");
        telemetryM.debug("After stopping, the forward zero power acceleration (natural deceleration) will be displayed.");
        telemetryM.debug("Press B on Gamepad 1 to stop.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    /** This starts the OpMode by setting the drive motors to run forward at full power. */
    @Override
    public void start() {
        follower.startTeleopDrive(false);
        follower.update();
        follower.setTeleOpDrive(1,0,0,true);
    }

    /**
     * This runs the OpMode. At any point during the running of the OpMode, pressing B on
     * game pad 1 will stop the OpMode. When the robot hits the specified velocity, the robot will
     * record its deceleration / negative acceleration until it stops. Then, it will average all the
     * recorded deceleration / negative acceleration and print that value.
     */
    @Override
    public void loop() {
        if (gamepad1.bWasPressed()) {
            stopRobot();
            requestOpModeStop();
        }

        follower.update();
        drawCurrentAndHistory();

        Vector heading = new Vector(1.0, follower.getPose().getHeading());
        if (!end) {
            if (!stopping) {
                if (follower.getVelocity().dot(heading) > VELOCITY) {
                    previousVelocity = follower.getVelocity().dot(heading);
                    previousTimeNano = System.nanoTime();
                    stopping = true;
                    follower.setTeleOpDrive(0,0,0,true);
                }
            } else {
                double currentVelocity = follower.getVelocity().dot(heading);
                accelerations.add((currentVelocity - previousVelocity) / ((System.nanoTime() - previousTimeNano) / Math.pow(10.0, 9)));
                previousVelocity = currentVelocity;
                previousTimeNano = System.nanoTime();
                if (currentVelocity < follower.getConstraints().getVelocityConstraint()) {
                    end = true;
                }
            }
        } else {
            double average = 0;
            for (double acceleration : accelerations) {
                average += acceleration;
            }
            average /= accelerations.size();

            telemetryM.debug("Forward Zero Power Acceleration (Deceleration): " + average);
            telemetryM.debug("\n");
            telemetryM.debug("Press A to set the Forward Zero Power Acceleration temporarily (while robot remains on).");
            telemetryM.debug("To keep it: add .forwardZeroPowerAcceleration(...) to THIS robot's follower set in Constants.java.");
            telemetryM.update(telemetry);

            if (gamepad1.aWasPressed()) {
                follower.getConstants().setForwardZeroPowerAcceleration(average);
                String message = "Forward Zero Power Acceleration: " + average;
                changes.add(message);
            }
        }
    }
}

/**
 * This is the LateralZeroPowerAccelerationTuner autonomous follower OpMode. This runs the robot
 * to the right until a specified velocity is achieved. Then, the robot cuts power to the motors, setting
 * them to zero power. The deceleration, or negative acceleration, is then measured until the robot
 * stops. The accelerations across the entire time the robot is slowing down is then averaged and
 * that number is then printed. This is used to determine how the robot will decelerate in the
 * forward direction when power is cut, making the estimations used in the calculations for the
 * drive Vector more accurate and giving better braking at the end of Paths.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @author Baron Henderson - 20077 The Indubitables
 * @version 1.0, 3/13/2024
 */
@Configurable
class LateralZeroPowerAccelerationTuner extends OpMode {
    private final ArrayList<Double> accelerations = new ArrayList<>();
    public static double VELOCITY = 30;
    private double previousVelocity;
    private long previousTimeNano;
    private boolean stopping;
    private boolean end;

    @Override
    public void init() {}

    /** This initializes the drive motors as well as the Panels telemetry. */
    @Override
    public void init_loop() {
        telemetryM.debug("The robot will run to the right until it reaches " + VELOCITY + " inches per second.");
        telemetryM.debug("Then, it will cut power from the drivetrain and roll to a stop.");
        telemetryM.debug("Make sure you have enough room.");
        telemetryM.debug("After stopping, the lateral zero power acceleration (natural deceleration) will be displayed.");
        telemetryM.debug("Press B on game pad 1 to stop.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    /** This starts the OpMode by setting the drive motors to run forward at full power. */
    @Override
    public void start() {
        follower.startTeleopDrive(false);
        follower.update();
        follower.setTeleOpDrive(0,1,0,true);
    }

    /**
     * This runs the OpMode. At any point during the running of the OpMode, pressing B on
     * game pad 1 will stop the OpMode. When the robot hits the specified velocity, the robot will
     * record its deceleration / negative acceleration until it stops. Then, it will average all the
     * recorded deceleration / negative acceleration and print that value.
     */
    @Override
    public void loop() {
        if (gamepad1.bWasPressed()) {
            stopRobot();
            requestOpModeStop();
        }

        follower.update();
        drawCurrentAndHistory();

        Vector heading = new Vector(1.0, follower.getPose().getHeading() - Math.PI / 2);
        if (!end) {
            if (!stopping) {
                if (Math.abs(follower.getVelocity().dot(heading)) > VELOCITY) {
                    previousVelocity = Math.abs(follower.getVelocity().dot(heading));
                    previousTimeNano = System.nanoTime();
                    stopping = true;
                    follower.setTeleOpDrive(0,0,0,true);
                }
            } else {
                double currentVelocity = Math.abs(follower.getVelocity().dot(heading));
                accelerations.add((currentVelocity - previousVelocity) / ((System.nanoTime() - previousTimeNano) / Math.pow(10.0, 9)));
                previousVelocity = currentVelocity;
                previousTimeNano = System.nanoTime();
                if (currentVelocity < follower.getConstraints().getVelocityConstraint()) {
                    end = true;
                }
            }
        } else {
            double average = 0;
            for (double acceleration : accelerations) {
                average += acceleration;
            }
            average /= accelerations.size();

            telemetryM.debug("Lateral Zero Power Acceleration (Deceleration): " + average);
            telemetryM.debug("\n");
            telemetryM.debug("Press A to set the Lateral Zero Power Acceleration temporarily (while robot remains on).");
            telemetryM.debug("To keep it: add .lateralZeroPowerAcceleration(...) to THIS robot's follower set in Constants.java.");
            telemetryM.update(telemetry);

            if (gamepad1.aWasPressed()) {
                follower.getConstants().setLateralZeroPowerAcceleration(average);
                String message = "Lateral Zero Power Acceleration: " + average;
                changes.add(message);
            }
        }
    }
}

/**
 * This is the Translational PIDF Tuner OpMode. It will keep the robot in place.
 * The user should push the robot laterally to test the PIDF and adjust the PIDF values accordingly.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/12/2024
 */
@Configurable
class TranslationalTuner extends OpMode {
    public static double DISTANCE = 40;
    private boolean forward = true;

    private Path forwards;
    private Path backwards;

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /** This initializes the Follower and creates the forward and backward Paths. */
    @Override
    public void init_loop() {
        telemetryM.debug("This will activate the translational PIDF(s)");
        telemetryM.debug("The robot will try to stay in place while you push it laterally.");
        telemetryM.debug("You can adjust the PIDF values to tune the robot's translational PIDF(s).");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.deactivateAllPIDFs();
        follower.activateTranslational();
        forwards = new Path(new BezierLine(at(0, 0), at(DISTANCE, 0)));
        forwards.setConstantHeadingInterpolation(0);
        backwards = new Path(new BezierLine(at(DISTANCE, 0), at(0, 0)));
        backwards.setConstantHeadingInterpolation(0);
        follower.followPath(forwards);
    }

    /** This runs the OpMode, updating the Follower as well as printing out the debug statements to the Telemetry */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (!follower.isBusy()) {
            if (forward) {
                forward = false;
                follower.followPath(backwards);
            } else {
                forward = true;
                follower.followPath(forwards);
            }
        }

        telemetryM.debug("Push the robot laterally to test the Translational PIDF(s).");
        telemetryM.update(telemetry);
    }
}

/**
 * This is the Heading PIDF Tuner OpMode. It will keep the robot in place.
 * The user should try to turn the robot to test the PIDF and adjust the PIDF values accordingly.
 * It will try to keep the robot at a constant heading while the user tries to turn it.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/12/2024
 */
@Configurable
class HeadingTuner extends OpMode {
    public static double DISTANCE = 40;
    private boolean forward = true;

    private Path forwards;
    private Path backwards;

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /**
     * This initializes the Follower and creates the forward and backward Paths. Additionally, this
     * initializes the Panels telemetry.
     */
    @Override
    public void init_loop() {
        telemetryM.debug("This will activate the heading PIDF(s).");
        telemetryM.debug("The robot will try to stay at a constant heading while you try to turn it.");
        telemetryM.debug("You can adjust the PIDF values to tune the robot's heading PIDF(s).");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.deactivateAllPIDFs();
        follower.activateHeading();
        forwards = new Path(new BezierLine(at(0, 0), at(DISTANCE, 0)));
        forwards.setConstantHeadingInterpolation(0);
        backwards = new Path(new BezierLine(at(DISTANCE, 0), at(0, 0)));
        backwards.setConstantHeadingInterpolation(0);
        follower.followPath(forwards);
    }

    /**
     * This runs the OpMode, updating the Follower as well as printing out the debug statements to
     * the Telemetry, as well as the Panels.
     */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (!follower.isBusy()) {
            if (forward) {
                forward = false;
                follower.followPath(backwards);
            } else {
                forward = true;
                follower.followPath(forwards);
            }
        }

        telemetryM.debug("Turn the robot manually to test the Heading PIDF(s).");
        telemetryM.update(telemetry);
    }
}

/**
 * This is the Drive PIDF Tuner OpMode. It will run the robot in a straight line going forward and back.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/12/2024
 */
@Configurable
class DriveTuner extends OpMode {
    public static double DISTANCE = 40;
    private boolean forward = true;

    private PathChain forwards;
    private PathChain backwards;

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /**
     * This initializes the Follower and creates the forward and backward Paths. Additionally, this
     * initializes the Panels telemetry.
     */
    @Override
    public void init_loop() {
        telemetryM.debug("This will run the robot in a straight line going " + DISTANCE + "inches forward.");
        telemetryM.debug("The robot will go forward and backward continuously along the path.");
        telemetryM.debug("Make sure you have enough room.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.deactivateAllPIDFs();
        follower.activateDrive();
        
        forwards = follower.pathBuilder()
                .setGlobalDeceleration()
                .addPath(new BezierLine(at(0, 0), at(DISTANCE, 0)))
                .setConstantHeadingInterpolation(0)
                .build();

        backwards = follower.pathBuilder()
                .setGlobalDeceleration()
                .addPath(new BezierLine(at(DISTANCE, 0), at(0, 0)))
                .setConstantHeadingInterpolation(0)
                .build();

        follower.followPath(forwards);
    }

    /**
     * This runs the OpMode, updating the Follower as well as printing out the debug statements to
     * the Telemetry, as well as the Panels.
     */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (!follower.isBusy()) {
            if (forward) {
                forward = false;
                follower.followPath(backwards);
            } else {
                forward = true;
                follower.followPath(forwards);
            }
        }

        telemetryM.debug("Driving forward?: " + forward);
        telemetryM.update(telemetry);
    }
}

/**
 * This is the Line Test Tuner OpMode. It will drive the robot forward and back
 * The user should push the robot laterally and angular to test out the drive, heading, and translational PIDFs.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/12/2024
 */
@Configurable
class Line extends OpMode {
    public static double DISTANCE = 40;
    private boolean forward = true;

    private Path forwards;
    private Path backwards;

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /** This initializes the Follower and creates the forward and backward Paths. */
    @Override
    public void init_loop() {
        telemetryM.debug("This will activate all the PIDF(s)");
        telemetryM.debug("The robot will go forward and backward continuously along the path while correcting.");
        telemetryM.debug("You can adjust the PIDF values to tune the robot's drive PIDF(s).");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.activateAllPIDFs();
        forwards = new Path(new BezierLine(at(0, 0), at(DISTANCE, 0)));
        forwards.setConstantHeadingInterpolation(0);
        backwards = new Path(new BezierLine(at(DISTANCE, 0), at(0, 0)));
        backwards.setConstantHeadingInterpolation(0);
        follower.followPath(forwards);
    }

    /** This runs the OpMode, updating the Follower as well as printing out the debug statements to the Telemetry */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (!follower.isBusy()) {
            if (forward) {
                forward = false;
                follower.followPath(backwards);
            } else {
                forward = true;
                follower.followPath(forwards);
            }
        }

        telemetryM.debug("Driving Forward?: " + forward);
        telemetryM.update(telemetry);
    }
}

/**
 * This is the Centripetal Tuner OpMode. It runs the robot in a specified distance
 * forward and to the left. On reaching the end of the forward Path, the robot runs the backward
 * Path the same distance back to the start. Rinse and repeat! This is good for testing a variety
 * of Vectors, like the drive Vector, the translational Vector, the heading Vector, and the
 * centripetal Vector.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/13/2024
 */
@Configurable
class CentripetalTuner extends OpMode {
    public static double DISTANCE = 20;
    private boolean forward = true;

    private Path forwards;
    private Path backwards;

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /**
     * This initializes the Follower and creates the forward and backward Paths.
     * Additionally, this initializes the Panels telemetry.
     */
    @Override
    public void init_loop() {
        telemetryM.debug("This will run the robot in a curve going " + DISTANCE + " inches to the left and the same number of inches forward.");
        telemetryM.debug("The robot will go continuously along the path.");
        telemetryM.debug("Make sure you have enough room.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void start() {
        follower.activateAllPIDFs();
        forwards = new Path(new BezierCurve(at(0, 0), at(Math.abs(DISTANCE), 0), at(Math.abs(DISTANCE), DISTANCE)));
        backwards = new Path(new BezierCurve(at(Math.abs(DISTANCE), DISTANCE), at(Math.abs(DISTANCE), 0), at(0, 0)));

        backwards.setTangentHeadingInterpolation();
        backwards.reverseHeadingInterpolation();

        follower.followPath(forwards);
    }

    /**
     * This runs the OpMode, updating the Follower as well as printing out the debug statements to
     * the Telemetry, as well as the Panels.
     */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();
        if (!follower.isBusy()) {
            if (forward) {
                forward = false;
                follower.followPath(backwards);
            } else {
                forward = true;
                follower.followPath(forwards);
            }
        }

        telemetryM.debug("Driving away from the origin along the curve?: " + forward);
        telemetryM.update(telemetry);
    }
}

/**
 * This is the Triangle autonomous OpMode.
 * It runs the robot in a triangle, with the starting point being the bottom-middle point.
 *
 * @author Baron Henderson - 20077 The Indubitables
 * @author Samarth Mahapatra - 1002 CircuitRunners Robotics Surge
 * @version 1.0, 12/30/2024
 */
class Triangle extends OpMode {

    private final Pose startPose = at(0, 0, Math.toRadians(0));
    private final Pose interPose = at(24, -24, Math.toRadians(90));
    private final Pose endPose = at(24, 24, Math.toRadians(45));

    private PathChain triangle;

    /**
     * This runs the OpMode, updating the Follower as well as printing out the debug statements to
     * the Telemetry, as well as the Panels.
     */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (follower.atParametricEnd()) {
            follower.followPath(triangle, true);
        }
    }

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    @Override
    public void init_loop() {
        telemetryM.debug("This will run in a roughly triangular shape, starting on the bottom-middle point.");
        telemetryM.debug("So, make sure you have enough space to the left, front, and right to run the OpMode.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    /** Creates the PathChain for the "triangle".*/
    @Override
    public void start() {
        follower.setStartingPose(startPose);

        triangle = follower.pathBuilder()
                .addPath(new BezierLine(startPose, interPose))
                .setLinearHeadingInterpolation(startPose.getHeading(), interPose.getHeading())
                .addPath(new BezierLine(interPose, endPose))
                .setLinearHeadingInterpolation(interPose.getHeading(), endPose.getHeading())
                .addPath(new BezierLine(endPose, startPose))
                .setLinearHeadingInterpolation(endPose.getHeading(), startPose.getHeading())
                .build();

        follower.followPath(triangle);
    }
}

/**
 * This is the Circle autonomous OpMode. It runs the robot in a PathChain that's actually not quite
 * a circle, but some Bezier curves that have control points set essentially in a square. However,
 * it turns enough to tune your centripetal force correction and some of your heading. Some lag in
 * heading is to be expected.
 *
 * @author Anyi Lin - 10158 Scott's Bots
 * @author Aaron Yang - 10158 Scott's Bots
 * @author Harrison Womack - 10158 Scott's Bots
 * @version 1.0, 3/12/2024
 */
@Configurable
class Circle extends OpMode {
    public static double RADIUS = 10;
    private PathChain circle;

    public void start() {
        // The robot faces this point the whole way round, so it moves with the circle.
        Pose center = at(0, RADIUS);
        circle = follower.pathBuilder()
                .addPath(new BezierCurve(at(0, 0), at(RADIUS, 0), at(RADIUS, RADIUS)))
                .setHeadingInterpolation(HeadingInterpolator.facingPoint(center.getX(), center.getY()))
                .addPath(new BezierCurve(at(RADIUS, RADIUS), at(RADIUS, 2 * RADIUS), at(0, 2 * RADIUS)))
                .setHeadingInterpolation(HeadingInterpolator.facingPoint(center.getX(), center.getY()))
                .addPath(new BezierCurve(at(0, 2 * RADIUS), at(-RADIUS, 2 * RADIUS), at(-RADIUS, RADIUS)))
                .setHeadingInterpolation(HeadingInterpolator.facingPoint(center.getX(), center.getY()))
                .addPath(new BezierCurve(at(-RADIUS, RADIUS), at(-RADIUS, 0), at(0, 0)))
                .setHeadingInterpolation(HeadingInterpolator.facingPoint(center.getX(), center.getY()))
                .build();
        follower.followPath(circle);
    }

    @Override
    public void init_loop() {
        telemetryM.debug("This will run in a roughly circular shape of radius " + RADIUS + ", starting on the right-most edge. ");
        telemetryM.debug("So, make sure you have enough space to the left, front, and back to run the OpMode.");
        telemetryM.debug("It will also continuously face the center of the circle to test your heading and centripetal correction.");
        telemetryM.update(telemetry);
        follower.update();
        drawCurrent();
    }

    @Override
    public void init() {
        // Anchor at field center so Panels draws the trace in the middle, not down in the corner.
        // The paths below are built with at(), from the same START — the two must move together.
        startCentered();
    }

    /**
     * This runs the OpMode, updating the Follower as well as printing out the debug statements to
     * the Telemetry, as well as the FTC Dashboard.
     */
    @Override
    public void loop() {
        follower.update();
        drawCurrentAndHistory();

        if (follower.atParametricEnd()) {
            follower.followPath(circle);
        }
    }
}

/**
 * Spins the robot 180° and calculates forwardPodY and strafePodX offsets for the Pinpoint localizer.
 * Prerequisite: both offsets must be set to 0 in this robot's pinpoint set in Constants.java.
 * After running, record the telemetered values into THAT SAME set — compPinpointConstants on the
 * comp robot, testPinpointConstants on the test bot. The banner names the robot you are on.
 *
 * @author Havish Sripada - 12808 RevAmped Robotics
 * @author Baron Henderson - 20077 The Indubitables
 */
class OffsetsTuner extends OpMode {
    @Override
    public void init() {
        startCentered();
        follower.update();
        drawCurrent();
    }

    @Override
    public void init_loop() {
        telemetryM.debug("Prerequisite: both offsets must be 0 in THIS robot's pinpoint set in Constants.java.");
        telemetryM.debug("Spin the robot " + Math.PI + " radians (180°). Offsets appear in telemetry.");
        telemetryM.update(telemetry);
        drawCurrent();
    }

    @Override
    public void loop() {
        follower.update();
        telemetryM.debug("Total Angle: " + follower.getTotalHeading());
        telemetryM.debug("Record these into THIS robot's pinpoint set in Constants.java:");
        telemetryM.debug("strafePodX: " + ((72.0 - follower.getPose().getX()) / 2.0));
        telemetryM.debug("forwardPodY: " + ((72.0 - follower.getPose().getY()) / 2.0));
        telemetryM.update(telemetry);
        drawCurrentAndHistory();
    }
}

/**
 * This is the Drawing class. It handles the drawing of stuff on Panels Dashboard, like the robot.
 *
 * @author Lazar - 19234
 * @version 1.1, 5/19/2025
 */
class Drawing {
    public static final double ROBOT_RADIUS = 9; // woah
    private static final FieldManager panelsField = PanelsField.INSTANCE.getField();

    private static final Style robotLook = new Style(
            "", "#FF0000", 2.0
    );
    private static final Style historyLook = new Style(
            "", "#4CAF50", 1.5
    );

    /**
     * This prepares Panels Field for using Pedro Offsets
     */
    public static void init() {
        panelsField.setOffsets(PanelsField.INSTANCE.getPresets().getPEDRO_PATHING());
    }

    /**
     * This draws everything that will be used in the Follower's telemetryDebug() method. This takes
     * a Follower as an input, so an instance of the DashbaordDrawingHandler class is not needed.
     *
     * @param follower Pedro Follower instance.
     */
    public static void drawDebug(Follower follower) {
        if (follower.getCurrentPath() != null) {
            drawPath(follower.getCurrentPath(), robotLook);
            Pose closestPoint = follower.getPointFromPath(follower.getCurrentPath().getClosestPointTValue());
            drawRobot(new Pose(closestPoint.getX(), closestPoint.getY(), follower.getCurrentPath().getHeadingGoal(follower.getCurrentPath().getClosestPointTValue())), robotLook);
        }
        drawPoseHistory(follower.getPoseHistory(), historyLook);
        drawRobot(follower.getPose(), historyLook);

        sendPacket();
    }

    /**
     * This draws a robot at a specified Pose with a specified
     * look. The heading is represented as a line.
     *
     * @param pose  the Pose to draw the robot at
     * @param style the parameters used to draw the robot with
     */
    public static void drawRobot(Pose pose, Style style) {
        if (pose == null || Double.isNaN(pose.getX()) || Double.isNaN(pose.getY()) || Double.isNaN(pose.getHeading())) {
            return;
        }

        panelsField.setStyle(style);
        panelsField.moveCursor(pose.getX(), pose.getY());
        panelsField.circle(ROBOT_RADIUS);

        Vector v = pose.getHeadingAsUnitVector();
        v.setMagnitude(v.getMagnitude() * ROBOT_RADIUS);
        double x1 = pose.getX() + v.getXComponent() / 2, y1 = pose.getY() + v.getYComponent() / 2;
        double x2 = pose.getX() + v.getXComponent(), y2 = pose.getY() + v.getYComponent();

        panelsField.setStyle(style);
        panelsField.moveCursor(x1, y1);
        panelsField.line(x2, y2);
    }

    /**
     * This draws a robot at a specified Pose. The heading is represented as a line.
     *
     * @param pose the Pose to draw the robot at
     */
    public static void drawRobot(Pose pose) {
        drawRobot(pose, robotLook);
    }

    /**
     * This draws a Path with a specified look.
     *
     * @param path  the Path to draw
     * @param style the parameters used to draw the Path with
     */
    public static void drawPath(Path path, Style style) {
        double[][] points = path.getPanelsDrawingPoints();

        for (int i = 0; i < points[0].length; i++) {
            for (int j = 0; j < points.length; j++) {
                if (Double.isNaN(points[j][i])) {
                    points[j][i] = 0;
                }
            }
        }

        panelsField.setStyle(style);
        panelsField.moveCursor(points[0][0], points[0][1]);
        panelsField.line(points[1][0], points[1][1]);
    }

    /**
     * This draws all the Paths in a PathChain with a
     * specified look.
     *
     * @param pathChain the PathChain to draw
     * @param style     the parameters used to draw the PathChain with
     */
    public static void drawPath(PathChain pathChain, Style style) {
        for (int i = 0; i < pathChain.size(); i++) {
            drawPath(pathChain.getPath(i), style);
        }
    }

    /**
     * This draws the pose history of the robot.
     *
     * @param poseTracker the PoseHistory to get the pose history from
     * @param style       the parameters used to draw the pose history with
     */
    public static void drawPoseHistory(PoseHistory poseTracker, Style style) {
        panelsField.setStyle(style);

        int size = poseTracker.getXPositionsArray().length;
        for (int i = 0; i < size - 1; i++) {

            panelsField.moveCursor(poseTracker.getXPositionsArray()[i], poseTracker.getYPositionsArray()[i]);
            panelsField.line(poseTracker.getXPositionsArray()[i + 1], poseTracker.getYPositionsArray()[i + 1]);
        }
    }

    /**
     * This draws the pose history of the robot.
     *
     * @param poseTracker the PoseHistory to get the pose history from
     */
    public static void drawPoseHistory(PoseHistory poseTracker) {
        drawPoseHistory(poseTracker, historyLook);
    }

    /**
     * This tries to send the current packet to FTControl Panels.
     */
    public static void sendPacket() {
        panelsField.update();
    }
}
