package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.telemetry.PanelsTelemetry;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.util.RobotLog;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.groups.Groups.sequential;

import com.pedropathing.ivy.Command;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import org.firstinspires.ftc.teamcode.config.AutonFieldTweaks;
import org.firstinspires.ftc.teamcode.diagnostics.DiagnosticsCenter;
import org.firstinspires.ftc.teamcode.framework.IvyOpMode;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * AutonomousExample — shows the command-tree structure (CLAUDE.md §3) that REPLACES a hand-rolled
 * state machine. The routine reads top-to-bottom like a plan.
 *
 * ALLIANCE / START POSE / FIELD (§9): chosen in ONE place via {@link AutonMenu} during init, so the
 * robot never runs from the wrong pose. No source edits between matches — the driver picks on the
 * Driver Hub before pressing START.
 *
 * PEDRO: build the follower with Constants.createFollower, and compose FollowPathCommand instances
 * into the tree below with Ivy's sequential / parallel groups. PollenAuto is a working example.
 *
 * COMMAND LOGGING: turn on TuningConfig.verboseTelemetry and every one of our commands logs its
 * start and end to the RC log (see framework/TeamCommand).
 */
@Autonomous(name = "34672 Auto (example)")
public class AutonomousExample extends IvyOpMode {

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private AutonMenu menu;
    private RobotIdentity robotId;
    // Built once at init; reused each loop (§4 rule 8, no per-loop alloc). idBanner (plain) goes to
    // Panels, which has no HTML display-format concept; idBannerHtml (larger/bold/colored) goes to
    // the Driver Station, which does.
    private String idBanner;
    private String idBannerHtml;

    // Match context — read from the menu when START is pressed. String fields so they land in the
    // snapshot cleanly.
    private String selectedAlliance = "UNKNOWN";
    private String selectedStartPose = "UNKNOWN";
    private String selectedField = "UNKNOWN";
    private int selectedDelaySeconds = 0;
    private double startBatteryVolts = 0.0;

    @Override
    public void initialize() {
        // Hardware first, so the menu can render while init is running.
        bulkReads = new BulkReads(hardwareMap);

        // Which robot is this? Read once, from the hub network name (see RobotIdentity).
        robotId = RobotIdentity.resolve();
        idBanner = robotId.banner();
        idBannerHtml = robotId.bannerHtml();
        // Enables the "subset of HTML tags" idBannerHtml relies on for larger/colored text. Affects
        // the whole Driver Station panel (incl. the AutonMenu below), not just this line — other
        // lines have no tags, so they render unchanged.
        telemetry.setDisplayFormat(Telemetry.DisplayFormat.HTML);

        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry); // fires once every 14 days, silent otherwise
        drivetrain = new Drivetrain(hardwareMap);
        menu = new AutonMenu(telemetry);

        // DiagnosticsCenter is a singleton that may have been built by an earlier OpMode, so it did
        // not register itself this time. Add it, so its periodic() (expiry cleanup) runs every loop.
        register(DiagnosticsCenter.get());

        // TODO: create the Pedro follower here; setStartingPose after the menu selection below.

        // ---- Pre-match selection loop -------------------------------------------------
        // Reads the dpad, updates the menu on the Driver Hub, waits for the driver to press START.
        // IvyOpMode extends LinearOpMode, so isStarted()/isStopRequested() work here.
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

        Persistence.writeSnapshot(snapshot(), hardwareMap); // AUTO-EXPORT on init is safe (§7)

        // The whole autonomous, as a composed command tree. No switch statement.
        Command routine = routine();
        if (selectedDelaySeconds > 0) {
            routine = sequential(waitMs(selectedDelaySeconds * 1000.0), routine);
        }
        schedule(routine);
    }

    private Command routine() {
        return sequential(
                // TODO: add Pedro FollowPathCommand instances and game mechanism commands here.
                // e.g. parallel(new FollowPathCommand(follower, path), mechanism.grabCommand())
        );
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (§4).
        // Tunables need nothing here: Panels writes straight into the statics this loop reads
        // (§6 Tier 1), so a dashboard edit is live with no per-loop work at all.
        bulkReads.clear();
        if (startBatteryVolts == 0.0) {
            startBatteryVolts = Persistence.readBatteryVolts(hardwareMap);
            // Deferred here (not init) because the voltage sensor reads 0.0 too early during init.
            // It's an uncached hardware round-trip (voltage reads aren't covered by BulkReads), so
            // reset the timer right after paying that one-time cost — otherwise it wrongly counts
            // toward every session's maxLoopMs, matching LoopTimer.reset()'s own documented intent.
            loopTimer.reset();
        }

        super.run(); // subsystem periodics (incl. DiagnosticsCenter), triggers, then Ivy's scheduler

        // Loop-time readout is REQUIRED (§0 prime directive, §4 rule 7). Pass numbers, not strings (§4 rule 8).
        loopTimer.update();
        // Here for one rule with no exceptions: EVERY OpMode polls, so no tunable can be lost by
        // being turned in the wrong OpMode. Nobody turns knobs during a real 30-second auto, so this
        // is the first call to switch off (TuningConfig.autosaveTunables) if loop budget gets tight.
        Persistence.pollAutosave(robotId, System.nanoTime());
        // Robot identity banner FIRST — larger/colored on the Driver Hub (HTML), plain text mirrored
        // to Panels. Pre-built strings, so no per-loop allocation (§4 rule 8).
        telemetry.addLine(idBannerHtml);
        PanelsTelemetry.INSTANCE.getTelemetry().debug(idBanner);
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());
        telemetry.addData("Alliance", selectedAlliance);
        DiagnosticsCenter.telemetry(telemetry); // health at a glance (§5)
        telemetry.update();
    }

    @Override
    public void reset() {
        drivetrain.stop();
        Persistence.saveTuning(robotId);
        Persistence.writeSnapshot(snapshot(), hardwareMap); // post-match record (§7)
        super.reset(); // clears Ivy's scheduler, subsystems and triggers
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
        s.captureLoop(loopTimer); // loop-time trend data (§0). At init, values are 0 — that's fine.
        return s;
    }
}
