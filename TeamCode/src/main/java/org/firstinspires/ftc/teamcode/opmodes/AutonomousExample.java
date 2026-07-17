package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.command.Command;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;
import com.seattlesolvers.solverslib.command.SequentialCommandGroup;
import com.seattlesolvers.solverslib.command.WaitCommand;

import org.firstinspires.ftc.teamcode.config.AutonFieldTweaks;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.diagnostics.DiagnosticsCenter;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;

/**
 * AutonomousExample — shows the command-tree structure (CLAUDE.md §3) that REPLACES a hand-rolled
 * state machine. The routine reads top-to-bottom like a plan.
 *
 * ALLIANCE / START POSE / FIELD (§9): chosen in ONE place via {@link AutonMenu} during init, so the
 * robot never runs from the wrong pose. No source edits between matches — the driver picks on the
 * Driver Hub before pressing START.
 *
 * PEDRO: add the follower here via SolversLib's pedroPathing module, and compose FollowPathCommand
 * instances into the tree below. Remember (§2): Pedro is installed ourselves at the latest version.
 */
@Autonomous(name = "34672 Auto (example)")
public class AutonomousExample extends CommandOpMode {

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private AutonMenu menu;

    // Match context — read from the menu when START is pressed. String fields so they land in the
    // snapshot cleanly.
    private String selectedAlliance = "UNKNOWN";
    private String selectedStartPose = "UNKNOWN";
    private String selectedField = "UNKNOWN";
    private int selectedDelaySeconds = 0;

    @Override
    public void initialize() {
        // Hardware first, so the menu can render while init is running.
        bulkReads = new BulkReads(hardwareMap);
        drivetrain = new Drivetrain(hardwareMap);
        menu = new AutonMenu(telemetry);

        // Command lifecycle logging — free traceability of when each command started, finished, or
        // was interrupted. Gated behind verboseTelemetry so match logs stay clean (§4 rule 8).
        // Pattern from decode-2025 (AutonBase.java lines 55-67).
        if (TuningConfig.verboseTelemetry) {
            CommandScheduler.getInstance().onCommandInitialize(
                    cmd -> RobotLog.i("Command Initialized: %s", cmd.getClass().getSimpleName()));
            CommandScheduler.getInstance().onCommandFinish(
                    cmd -> RobotLog.i("Command Finished: %s", cmd.getClass().getSimpleName()));
            CommandScheduler.getInstance().onCommandInterrupt(
                    cmd -> RobotLog.i("Command Interrupted: %s", cmd.getClass().getSimpleName()));
        }

        // Register the DiagnosticsCenter so its periodic() (expiry cleanup) runs every scheduler tick.
        register(DiagnosticsCenter.get());

        // TODO: create the Pedro follower here; setStartingPose after the menu selection below.

        // ---- Pre-match selection loop -------------------------------------------------
        // Reads the dpad, updates the menu on the Driver Hub, waits for the driver to press START.
        // CommandOpMode extends LinearOpMode, so isStarted()/isStopRequested() work here.
        while (!isStarted() && !isStopRequested()) {
            menu.loop(gamepad1);
            telemetry.addLine("Ready. Press START when set.");
            telemetry.update();
        }
        if (isStopRequested()) return;

        // Snap current selections into fields so run() and snapshot() don't re-read the menu.
        selectedAlliance = menu.getAlliance().name();
        selectedStartPose = menu.getStartPose().name();
        selectedField = menu.getField().name();
        selectedDelaySeconds = menu.getDelaySeconds();

        // Field tweaks — per (alliance × field) pose deltas so drift on one field doesn't force
        // retuning every path. Fetched now but applied to the follower's starting pose in the
        // Pedro wiring section above (still a TODO until Pedro is on the robot).
        AutonFieldTweaks tweaks = menu.getFieldTweaks();
        RobotLog.i("Auton config: alliance=%s startPose=%s field=%s delay=%ds  tweaks=(dx=%.2f, dy=%.2f, dh=%.2f°)",
                selectedAlliance, selectedStartPose, selectedField, selectedDelaySeconds,
                tweaks.xOffsetInches, tweaks.yOffsetInches, tweaks.headingOffsetDeg);

        Persistence.writeSnapshot(snapshot()); // AUTO-EXPORT on init is safe (§7)

        // The whole autonomous, as a composed command tree. No switch statement.
        Command routine = routine();
        if (selectedDelaySeconds > 0) {
            routine = new SequentialCommandGroup(new WaitCommand(selectedDelaySeconds * 1000L), routine);
        }
        schedule(routine);
        loopTimer.reset();
    }

    private Command routine() {
        return new SequentialCommandGroup(
                // TODO: add Pedro FollowPathCommand instances and game mechanism commands here.
                // e.g. new ParallelCommandGroup(new FollowPathCommand(follower, path), mechanism.grabCommand())
        );
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (§4).
        bulkReads.clear();

        super.run(); // command scheduler + subsystem periodics (incl. DiagnosticsCenter)

        // Loop-time readout is REQUIRED (§0 prime directive, §4 rule 7). Pass numbers, not strings (§4 rule 8).
        loopTimer.update();
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());
        telemetry.addData("Alliance", selectedAlliance);
        DiagnosticsCenter.telemetry(telemetry); // health at a glance (§5)
        telemetry.update();
    }

    @Override
    public void reset() {
        drivetrain.stop();
        Persistence.writeSnapshot(snapshot()); // post-match record (§7)
        CommandScheduler.getInstance().reset();
    }

    private Persistence.Snapshot snapshot() {
        Persistence.Snapshot s = new Persistence.Snapshot();
        s.alliance = selectedAlliance;
        s.startPose = selectedStartPose;
        // TODO: add tuning values here as you dial them in (CLAUDE.md §7)
        return s;
    }
}
