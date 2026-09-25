package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.pedropathing.api.Paths;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.command.Command;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;
import com.seattlesolvers.solverslib.command.SequentialCommandGroup;
import com.seattlesolvers.solverslib.command.WaitCommand;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.commands.FollowPathCommand;
import org.firstinspires.ftc.teamcode.config.AutonFieldTweaks;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.diagnostics.DiagnosticsCenter;
import org.firstinspires.ftc.teamcode.pedroPathing.Constants;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * PollenAuto — the five-segment route drawn in the Pedro Pathing Visualizer, ported onto our stack.
 *
 * WHAT IT DOES TODAY: drives the shape and nothing else. There is no intake action yet — the spots
 * where mechanism commands belong are marked TODO in {@link #routine()}. Running this scores
 * nothing; it proves the path.
 *
 * WHERE IT CAME FROM: a visualizer export (AutoPath), written for Pedro 3. We run Pedro 3.0.1 too,
 * so the path API is the export's own: Paths.line / Paths.curve, with .linear(...) and
 * .reverseTangent() for heading. Two things changed in the port, and only two:
 *
 *   1. SCHEDULER. The export ran Pedro's Ivy scheduler inside a LinearOpMode. We run ONE scheduler,
 *      SolversLib's (CLAUDE.md §2, §3), so each segment is a FollowPathCommand in a command tree.
 *      FollowPathCommand also carries the built-in timeout §5 requires; the export had none.
 *   2. ALLIANCE. The export hardcoded its poses and its start pose. Here every pose is authored for
 *      BLUE and goes through AutonMenu.resolve(), which mirrors for red and applies this field's
 *      measured tweaks (§9). Nothing in this file is alliance-specific.
 *
 * THE POSE NUMBERS ARE UNCHANGED from the export, to the fourth decimal.
 *
 * BEFORE YOU TRUST IT — three things the port cannot settle from a text file:
 *   - The poses must be the BLUE ones. If they were drawn on the red side, resolve() mirrors them
 *     into the wrong quarter of the field and every segment is wrong. Check this first.
 *   - Segments 2-5 drive BACKWARDS (reversed tangent). Watch the first run at reduced power.
 *   - Foresight (Pedro 3's follower) is UNTUNED on BOTH robots until AutoTune has run on each —
 *     the brake coefficients in Constants are placeholders. Expect loose following until then.
 */
@Configurable
@Autonomous(name = "34672 Pollen Auto")
public class PollenAuto extends CommandOpMode {

    // ===================================================================================
    // TUNABLES (§6 Tier 1 — turn these in Panels, no deploy)
    // ===================================================================================

    /**
     * Timeout for each path segment, in seconds. One knob for all five: the longest segment here is
     * about 70 inches, which even a slow follower covers well inside this.
     *
     * It is a safety net, not a schedule — a segment that hits it means the robot is stuck, and
     * FollowPathCommand logs loudly and moves on rather than hanging the tree for the match (§5).
     * Five segments at this value must still fit inside 30 seconds with the menu delay on top.
     */
    public static double segmentTimeoutSec = 8.0;

    // There is no per-path power cap any more. Pedro 3 sets the speed ceiling once, when the
    // follower is built, from Constants.compMaxPower / testMaxPower. For a slow first run, turn that
    // down in Panels BEFORE pressing INIT — changing it after init does nothing to this run.

    // ===================================================================================
    // THE ROUTE — authored for BLUE, in field inches, headings in degrees (§9)
    // ===================================================================================
    //
    // Straight from the visualizer export. Do not hand-write a red version of any of these: red is
    // derived by AutonMenu.resolve(). See AllianceMirror for why that rule has no exceptions.

    private static final Pose START         = blue(56.0000,   9.0000,   90.0000);
    private static final Pose PICKUP_POLLEN = blue(86.8614,  11.4041,  180.0000);
    private static final Pose POINT_2       = blue(31.4297,  49.0861,  -89.3982);
    private static final Pose POINT_3       = blue(32.0475, 104.1505,  -90.6428);
    private static final Pose POINT_4       = blue(54.8792, 122.8119, -140.7393);
    private static final Pose POINT_5       = blue(12.7188, 121.5327,    1.7379);

    /** Bezier control point that bows segment 2 out around the field. Its heading is unused. */
    private static final Pose POINT_2_CONTROL = blue(31.4337, 22.2455, 0.0);

    /** Poses are authored in degrees, the way the visualizer shows them; Pedro wants radians. */
    private static Pose blue(double xInches, double yInches, double headingDegrees) {
        return new Pose(xInches, yInches, Math.toRadians(headingDegrees));
    }

    // ===================================================================================

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private Follower follower;
    private AutonMenu menu;
    private RobotIdentity robotId;

    // Built once at init, reused every loop (§4 rule 8, no per-loop allocation).
    private String idBanner;
    private String idBannerHtml;

    // The five segments, built once at init after the alliance is known. Building a Path allocates;
    // doing it in the loop would be a §4 rule 8 violation and a sure GC hitch.
    private Path toPollen;
    private Path toPoint2;
    private Path toPoint3;
    private Path toPoint4;
    private Path toPoint5;

    // Match context, snapped when START is pressed.
    private String selectedAlliance = "UNKNOWN";
    private String selectedStartPose = "UNKNOWN";
    private String selectedField = "UNKNOWN";
    private int selectedDelaySeconds = 0;
    private double startBatteryVolts = 0.0;

    @Override
    public void initialize() {
        bulkReads = new BulkReads(hardwareMap);

        robotId = RobotIdentity.resolve();
        idBanner = robotId.banner();
        idBannerHtml = robotId.bannerHtml();
        telemetry.setDisplayFormat(Telemetry.DisplayFormat.HTML);

        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry);
        drivetrain = new Drivetrain(hardwareMap);
        menu = new AutonMenu(telemetry);

        if (TuningConfig.verboseTelemetry) {
            CommandScheduler.getInstance().onCommandInitialize(
                    cmd -> RobotLog.i("Command Initialized: %s", cmd.getClass().getSimpleName()));
            CommandScheduler.getInstance().onCommandFinish(
                    cmd -> RobotLog.i("Command Finished: %s", cmd.getClass().getSimpleName()));
            CommandScheduler.getInstance().onCommandInterrupt(
                    cmd -> RobotLog.i("Command Interrupted: %s", cmd.getClass().getSimpleName()));
        }

        register(DiagnosticsCenter.get());

        // The identity picks this robot's own Pedro tuning — comp and test drive differently (§6).
        // Its power ceiling is read here, once, so Constants.*MaxPower must be set before INIT.
        follower = Constants.createFollower(hardwareMap, robotId);

        // ---- Pre-match selection loop -------------------------------------------------
        while (!isStarted() && !isStopRequested()) {
            menu.loop(gamepad1);
            telemetry.addLine("Ready. Press START when set.");
            telemetry.update();
        }
        if (isStopRequested()) return;

        selectedAlliance = menu.getAlliance().name();
        selectedStartPose = menu.getStartPose().name();
        selectedField = menu.getField().name();
        selectedDelaySeconds = menu.getDelaySeconds();

        AutonFieldTweaks tweaks = menu.getFieldTweaks();
        RobotLog.i("Auton config: alliance=%s startPose=%s field=%s delay=%ds  tweaks=(dx=%.2f, dy=%.2f, dh=%.2f deg)",
                selectedAlliance, selectedStartPose, selectedField, selectedDelaySeconds,
                tweaks.xOffsetInches, tweaks.yOffsetInches, tweaks.headingOffsetDeg);

        // Resolve EVERY pose for the alliance we are actually playing, then build the paths from the
        // resolved poses. Order matters: mirror the poses, never a built path — a path's control
        // points are already baked (see AllianceMirror).
        Pose start  = menu.resolve(START);
        Pose pollen = menu.resolve(PICKUP_POLLEN);
        Pose p2     = menu.resolve(POINT_2);
        Pose p2Ctrl = menu.resolve(POINT_2_CONTROL);
        Pose p3     = menu.resolve(POINT_3);
        Pose p4     = menu.resolve(POINT_4);
        Pose p5     = menu.resolve(POINT_5);

        follower.setPose(start);
        buildPaths(start, pollen, p2, p2Ctrl, p3, p4, p5);

        RobotLog.i("PollenAuto start pose: (%.2f, %.2f) heading %.1f deg",
                start.x(), start.y(), Math.toDegrees(start.heading()));

        Persistence.writeSnapshot(snapshot(), hardwareMap); // AUTO-EXPORT on init is safe (§7)

        Command routine = routine();
        if (selectedDelaySeconds > 0) {
            routine = new SequentialCommandGroup(new WaitCommand(selectedDelaySeconds * 1000L), routine);
        }
        schedule(routine);
    }

    /**
     * Builds the five segments. Each is its own Path so each gets its own timeout and its own place
     * in the command tree — that is what lets a mechanism command sit between two of them.
     */
    private void buildPaths(Pose start, Pose pollen, Pose p2, Pose p2Ctrl, Pose p3, Pose p4, Pose p5) {
        // 1. Straight out to the pollen, turning from the start heading to the pickup heading on the
        //    way (linear heading interpolation, exactly as the export wrote it).
        toPollen = Paths.line(start, pollen).linear(start, pollen);

        // 2-5. REVERSED TANGENT: the robot follows the curve backwards, tail first.
        //      Paths.curve takes the Bezier points in order: start, control, end.
        toPoint2 = Paths.curve(pollen, p2Ctrl, p2).reverseTangent();
        toPoint3 = Paths.line(p2, p3).reverseTangent();
        toPoint4 = Paths.line(p3, p4).reverseTangent();
        toPoint5 = Paths.line(p4, p5).reverseTangent();
    }

    /**
     * The whole autonomous, as a composed command tree — no switch statement (§3). It reads
     * top-to-bottom as the plan the robot follows.
     */
    private Command routine() {
        return new SequentialCommandGroup(
                segment(toPollen),

                // TODO(mechanism): run the intake here to actually collect the pollen. Once the
                // intake is on the robot this auto runs on, construct it in initialize()
                //     intake = new Intake(hardwareMap);
                // and drop the command in:
                //     new IntakeCommand(intake).setTimeout(2.0)
                // To intake WHILE driving the next segment instead of standing still, wrap the two
                // in a ParallelCommandGroup rather than putting them in sequence.

                segment(toPoint2),
                segment(toPoint3),
                segment(toPoint4),

                // TODO(mechanism): this is where scoring belongs — point 5 is the end of the route.
                // Same shape as above: a subsystem command with its own built-in timeout.

                segment(toPoint5)
        );
    }

    /** One path segment, with the shared timeout applied. */
    private FollowPathCommand segment(Path path) {
        return new FollowPathCommand(follower, path)
                .setTimeout(segmentTimeoutSec);
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (§4).
        bulkReads.clear();
        if (startBatteryVolts == 0.0) {
            startBatteryVolts = Persistence.readBatteryVolts(hardwareMap);
            loopTimer.reset(); // don't let that one-time uncached read count toward maxLoopMs
        }

        // Follower first: it reads the Pinpoint (the one I2C read per loop, §4 rule 5) and drives
        // the wheels toward the current path. Running it before the scheduler means the isFinished()
        // checks below judge this loop's fresh pose, not last loop's.
        follower.update();

        super.run(); // command scheduler + subsystem periodics (incl. DiagnosticsCenter)

        loopTimer.update();
        Persistence.pollAutosave(robotId, System.nanoTime());

        telemetry.addLine(idBannerHtml);
        PanelsTelemetry.INSTANCE.getTelemetry().debug(idBanner);
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());
        telemetry.addData("Alliance", selectedAlliance);
        // Pose is the one thing worth watching on the Driver Hub during an auto: it tells you
        // straight away whether the robot thinks it is where it actually is.
        Pose pose = follower.pose();
        telemetry.addData("X in", pose.x());
        telemetry.addData("Y in", pose.y());
        telemetry.addData("Heading deg", Math.toDegrees(pose.heading()));
        DiagnosticsCenter.telemetry(telemetry); // health at a glance (§5)
        telemetry.update();
    }

    @Override
    public void reset() {
        // Stop the follower before the drivetrain, so the last thing written to the motors is zero.
        if (follower != null) follower.stop();
        drivetrain.stop();
        Persistence.saveTuning(robotId);
        Persistence.writeSnapshot(snapshot(), hardwareMap); // post-match record (§7)
        CommandScheduler.getInstance().reset();
    }

    private Persistence.Snapshot snapshot() {
        Persistence.Snapshot s = new Persistence.Snapshot();
        s.alliance = selectedAlliance;
        s.startPose = selectedStartPose;
        s.startingBatteryVolts = startBatteryVolts;
        if (robotId != null) {
            s.robot = robotId.robot.name();
            s.networkName = robotId.networkName;
        }
        s.captureLoop(loopTimer);
        return s;
    }
}
