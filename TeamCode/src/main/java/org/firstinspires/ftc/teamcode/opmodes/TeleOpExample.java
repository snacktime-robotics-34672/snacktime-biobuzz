package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.field.FieldManager;
import com.bylazar.field.PanelsField;
import com.bylazar.field.Style;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.pedropathing.follower.Follower;
import com.pedropathing.geometry.Pose;
import com.pedropathing.math.Vector;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;
import com.seattlesolvers.solverslib.gamepad.GamepadEx;
import com.seattlesolvers.solverslib.gamepad.GamepadKeys;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import org.firstinspires.ftc.teamcode.pedroPathing.Constants;
import org.firstinspires.ftc.teamcode.pedroPathing.PedroTuningStore;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;
import org.firstinspires.ftc.teamcode.util.StandYourGround;

/**
 * TeleOpExample — field-centric mecanum drive. LEFT_BUMPER = slow mode.
 *
 * Pedro reads the Pinpoint heading and rotates stick inputs to field coordinates each loop.
 * Driver Hub telemetry is minimal and glanceable (CLAUDE.md sections 4, 8).
 */
@TeleOp(name = "34672 TeleOp (example)")
public class TeleOpExample extends CommandOpMode {

    // Panels field view — built once, reused every loop (§4 rule 8, no per-loop allocation).
    // Without an explicit draw call the field graphic never moves, even though the X/Y/heading
    // telemetry numbers below are already correct (they're a separate channel from the field draw).
    private static final FieldManager panelsField = PanelsField.INSTANCE.getField();
    private static final Style robotLook = new Style("", "#FF0000", 2.0);
    private static final double ROBOT_RADIUS = 9;

    private final LoopTimer loopTimer = new LoopTimer();
    // Looked up once. Panels' TelemetryManager buffers lines until update() is called, so this
    // OpMode must call it every loop — see the note where it is called.
    private final TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private GamepadEx driver;
    private Follower follower;
    private double startBatteryVolts = 0.0;
    private RobotIdentity robotId;
    // Built once at init; reused each loop (§4 rule 8, no per-loop alloc). idBanner (plain) goes to
    // Panels, which has no HTML display-format concept; idBannerHtml (larger/bold/colored) goes to
    // the Driver Station, which does.
    private String idBanner;
    private String idBannerHtml;
    // The defensive brace: holds position whenever the driver lets go of the sticks.
    private final StandYourGround standYourGround = new StandYourGround();

    @Override
    public void initialize() {
        // MANUAL bulk caching — the biggest lever on loop time (section 0, section 4 rule 1).
        bulkReads = new BulkReads(hardwareMap);

        panelsField.setOffsets(PanelsField.INSTANCE.getPresets().getPEDRO_PATHING());

        // Which robot is this? Read once, from the hub network name (see RobotIdentity).
        robotId = RobotIdentity.resolve();
        idBanner = robotId.banner();
        idBannerHtml = robotId.bannerHtml();
        // Enables the "subset of HTML tags" idBannerHtml relies on for larger/colored text. Affects
        // the whole Driver Station panel, not just this line — other lines have no tags, so they
        // render unchanged.
        telemetry.setDisplayFormat(Telemetry.DisplayFormat.HTML);

        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry); // fires once every 14 days, silent otherwise

        drivetrain = new Drivetrain(hardwareMap);
        driver = new GamepadEx(gamepad1);

        // Pedro drives the wheels; startTeleopDrive() sets it to open-loop mode (§10).
        // The identity picks this robot's own Pedro tuning — comp and test drive differently.
        follower = Constants.createFollower(hardwareMap, robotId);
        telemetry.addLine(PedroTuningStore.lastStatus());
        follower.startTeleopDrive();

        // Fail loud rather than let two controllers fight over the same motors. Pedro's point-hold
        // governs heading while the brace is active, so heading correction has nothing to add and
        // would pull against it (§5 — say so clearly instead of starting degraded).
        if (Drivetrain.holdWhenIdleEnabled && Drivetrain.headingHoldEnabled) {
            telemetry.addLine("*** CONFLICT: turn OFF headingHoldEnabled — "
                    + "stand-your-ground already holds heading ***");
        }

        Persistence.Snapshot initSnap = new Persistence.Snapshot();
        initSnap.robot = robotId.robot.name();
        initSnap.networkName = robotId.networkName;
        Persistence.writeSnapshot(initSnap, hardwareMap); // safe: init, not the loop (section 7)
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (section 4).
        // Tunables need nothing here: Panels writes straight into the statics this loop reads
        // (section 6 Tier 1), so a dashboard edit is live with no per-loop work at all.
        bulkReads.clear();
        if (startBatteryVolts == 0.0) {
            startBatteryVolts = Persistence.readBatteryVolts(hardwareMap);
            // Deferred here (not init) because the voltage sensor reads 0.0 too early during init.
            // It's an uncached hardware round-trip (voltage reads aren't covered by BulkReads), so
            // reset the timer right after paying that one-time cost — otherwise it wrongly counts
            // toward every session's maxLoopMs, matching LoopTimer.reset()'s own documented intent.
            loopTimer.reset();
            // Same reasoning as the loop timer: init-time readings are not the match, so drop them.
            drivetrain.resetCurrentStats();
        }

        // Read -> process -> write (section 4, rule 2).
        double cap = driver.getButton(GamepadKeys.Button.LEFT_BUMPER)
                ? Drivetrain.driveSlowModeCap
                : Drivetrain.driveSpeedCap;

        // Field-centric: Pedro rotates strafe/forward by the Pinpoint heading before applying power.
        // Sign convention verified on-robot 2026-07-18: forward was inverted vs. PedroTeleOpSample's
        // -leftY (strafe/turn matched as-is). Pedro's Line test drove the correct physical direction
        // autonomously, so this is a TeleOp-mapping-only flip, not a motor-wiring issue.
        double dz = JoystickCurve.deadzone;
        double forward = applyDeadzone(driver.getLeftY(), dz);
        double strafe  = applyDeadzone(-driver.getLeftX(), dz);
        double turn    = applyDeadzone(-driver.getRightX(), dz);

        // STAND YOUR GROUND. Let go of the sticks and the robot braces on the spot instead of
        // coasting, so a push does not move us. Touch a stick and it hands control straight back.
        // While holding, Pedro is driving the wheels to the held pose, so issuing a manual drive
        // command would be fighting it — hence the branch rather than an unconditional call.
        boolean autoControlled = standYourGround.update(follower, forward, strafe, turn);
        if (!autoControlled) {
            follower.setTeleOpDrive(forward * cap, strafe * cap, turn * cap, false);
        }
        follower.update();

        // ─────────────────────────────────────────────────────────────────────────────────────
        // TODO: ROBOT HOLD (idle position-hold) — a defensive brace. NOT YET IMPLEMENTED.
        //
        //   Behavior: the instant all three drive inputs sit at zero (forward == strafe == turn == 0
        //   after deadzone), capture the CURRENT field pose ONCE and command the robot to actively
        //   hold exactly that x/y/heading — so an opponent trying to push us off a scoring spot gets
        //   fought back to where we were the moment the driver let go. Any input past the deadzone
        //   releases the hold and hands control straight back to manual driving; the next return to
        //   zero re-captures a fresh target.
        //
        //   CRUX — capture ONCE on entry, never re-capture while held: the target is "where the robot
        //   was at the instant the sticks hit zero." If you re-read the pose every loop, a steady push
        //   slowly walks the target and the brace is worthless. So: a small DRIVING <-> HOLDING state
        //   (§3 allows a local state machine for a genuine mode), capturing follower.getPose() only on
        //   the DRIVING->HOLDING transition.
        //
        //   MECHANISM: reuse Pedro's own point-hold rather than hand-rolling a controller — on entry
        //   call follower.holdPoint(capturedPose) (VERIFY the exact 2.1.2 signature against
        //   docs.seattlesolvers.com / Pedro docs — do not guess), and on release call
        //   follower.startTeleopDrive() to resume. This reuses the follower PIDFs tuned in Step 2, so
        //   hold quality rides on that same tuning — no second controller to tune.
        //
        //   INTERACTION: Pedro's holdPoint already governs heading, so it would fight HeadingCorrector
        //   (Drivetrain.headingHoldEnabled). Do NOT run both at once — the hold owns heading
        //   while active.
        //   LOOP COST (§0/§4): follower.update() is already the per-loop follower cost; capture the
        //   Pose only on the transition, not every loop, so no per-loop allocation is added.
        //   TUNABLE: gate behind a @Configurable flag (e.g. Drivetrain.holdWhenIdleEnabled).
        //   OPEN QUESTION for build time: auto-hold-on-zero (what Aaron described) vs. a hold-enable
        //   button — auto-hold can fight a driver making fine, sub-deadzone line-up nudges. Decide on
        //   the bench with a driver.
        // ─────────────────────────────────────────────────────────────────────────────────────

        // Runs the command scheduler + every subsystem's periodic().
        super.run();

        // Loop-time readout is REQUIRED (section 0 prime directive, section 4 rule 7).
        // Pass numbers, not hand-built strings (rule 8). Watch Loop Hz for regressions.
        loopTimer.update();
        // Save ANY tunable you change on the bench without waiting for a clean stop — Pedro's
        // constants included. Gated inside pollAutosave so the per-loop cost can be taken back for
        // a match if it ever matters (§0).
        Persistence.pollAutosave(robotId, System.nanoTime());
        // Robot identity banner FIRST, so "which robot am I on?" is always the top line — larger/
        // colored on the Driver Hub (HTML), plain text mirrored to Panels. Pre-built strings, so no
        // per-loop allocation (§4 rule 8).
        telemetry.addLine(idBannerHtml);
        panels.debug(idBanner);
        // Current mode, which §8 asks for on the Driver Hub — a driver needs to know at a glance
        // whether the robot is braced or free, because the two feel very different on the sticks.
        // Constant strings, so no per-loop allocation (§4 rule 8).
        telemetry.addData("Drive", driveModeLabel(standYourGround.getState()));
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());
        telemetry.addData("X in", follower.getPose().getX());
        telemetry.addData("Y in", follower.getPose().getY());
        telemetry.addData("Heading °", Math.toDegrees(follower.getPose().getHeading()));

        // Drive current. Off in Panels (Drivetrain.currentMonitorEnabled) removes both the readouts
        // and the four hub round-trips behind them. "Amp read ms" is what those reads cost, so the
        // price of watching is on screen next to the thing you are watching (§0).
        if (Drivetrain.currentMonitorEnabled) {
            telemetry.addData("Amps now", drivetrain.getTotalAmps());
            telemetry.addData("Amps max", drivetrain.getMaxTotalAmps());
            telemetry.addData("Amps avg", drivetrain.getMeanTotalAmps());
            telemetry.addData("Amp read ms", drivetrain.getAmpReadMs());
            panels.addData("Amps now", drivetrain.getTotalAmps());
            panels.addData("Amps max", drivetrain.getMaxTotalAmps());
            panels.addData("Amps avg", drivetrain.getMeanTotalAmps());
            panels.addData("Amp read ms", drivetrain.getAmpReadMs());
        }
        telemetry.update();

        // REQUIRED, and it was missing. Panels' TelemetryManager appends every line to a list and
        // only clears it in update(), which nothing here called — so the list grew by one string
        // per loop for the whole match and nothing we sent ever reached Panels. update() also
        // rate-limits the actual send on its own, so calling it every loop is both correct and
        // cheap.
        panels.update();

        // Moves the robot dot on the Panels field view. This is a network send every loop — a
        // deliberate loop-time cost, flagged per §0/§4 — but it's dev-dashboard telemetry, not the
        // Driver Hub set (rule 6), so it's the right place to pay it.
        drawRobot(follower.getPose());
        panelsField.update();
    }

    /** Draws the robot as a circle at pose, with a line showing heading (mirrors pedroPathing/Tuning.java's Drawing). */
    private static void drawRobot(Pose pose) {
        panelsField.setStyle(robotLook);
        panelsField.moveCursor(pose.getX(), pose.getY());
        panelsField.circle(ROBOT_RADIUS);

        Vector v = pose.getHeadingAsUnitVector();
        v.setMagnitude(v.getMagnitude() * ROBOT_RADIUS);
        panelsField.setStyle(robotLook);
        panelsField.moveCursor(pose.getX() + v.getXComponent() / 2, pose.getY() + v.getYComponent() / 2);
        panelsField.line(pose.getX() + v.getXComponent(), pose.getY() + v.getYComponent());
    }

    /**
     * Driver-facing name for the drive mode. Constant strings, so no per-loop allocation (§4 rule 8).
     * A driver needs this at a glance: the three modes feel completely different on the sticks.
     */
    private static String driveModeLabel(StandYourGround.State state) {
        switch (state) {
            case HOLDING: return "HOLDING (braced)";
            case YIELDED: return "AUTO (driving to a spot)";
            default:      return "manual";
        }
    }

    /** Returns 0 if |value| is within the deadzone, otherwise passes value through unchanged. */
    private static double applyDeadzone(double value, double deadzone) {
        return Math.abs(value) < deadzone ? 0.0 : value;
    }

    @Override
    public void reset() {
        standYourGround.reset(); // drop any hold before the follower stops driving
        follower.breakFollowing();
        drivetrain.stop();
        Persistence.saveTuning(robotId);
        Persistence.Snapshot stopSnap = new Persistence.Snapshot();
        stopSnap.robot = robotId.robot.name();
        stopSnap.networkName = robotId.networkName;
        stopSnap.startingBatteryVolts = startBatteryVolts;
        stopSnap.captureLoop(loopTimer); // loop-time trend data (§0)
        Persistence.writeSnapshot(stopSnap, hardwareMap); // post-match record (section 7)
        CommandScheduler.getInstance().reset();
    }
}
