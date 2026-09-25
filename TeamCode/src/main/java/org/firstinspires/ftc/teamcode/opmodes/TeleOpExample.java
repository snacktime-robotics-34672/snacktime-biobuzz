package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.field.FieldManager;
import com.bylazar.field.PanelsField;
import com.bylazar.field.Style;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.pedropathing.follower.Follower;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.follower.ManualDrive;
import com.pedropathing.math.Pose;
import com.seattlesolvers.solverslib.gamepad.GamepadEx;
import com.seattlesolvers.solverslib.gamepad.GamepadKeys;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.util.Locale;

import org.firstinspires.ftc.teamcode.pedroPathing.Constants;
import org.firstinspires.ftc.teamcode.commands.IntakeCommand;
import org.firstinspires.ftc.teamcode.framework.IvyOpMode;
import org.firstinspires.ftc.teamcode.framework.Trigger;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * TeleOpExample — field-centric mecanum drive. LEFT_BUMPER = slow mode.
 * RIGHT_TRIGGER = hold to run the intake.
 *
 * Pedro reads the Pinpoint heading and rotates stick inputs to field coordinates each loop.
 * Driver Hub telemetry is minimal and glanceable (CLAUDE.md sections 4, 8).
 */
@TeleOp(name = "34672 TeleOp (example)")
public class TeleOpExample extends IvyOpMode {

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
    private Intake intake;
    private GamepadEx driver;
    private Follower follower;
    private double startBatteryVolts = 0.0;
    private RobotIdentity robotId;
    // Built once at init; reused each loop (§4 rule 8, no per-loop alloc). Plain text, because its
    // only destination now is Panels — the Driver Hub banner was removed on 2026-09-23. The HTML
    // variant (RobotIdentity.bannerHtml) is still used by AutonomousExample and VisionCalibration.
    private String idBanner;

    @Override
    public void initialize() {
        // MANUAL bulk caching — the biggest lever on loop time (section 0, section 4 rule 1).
        bulkReads = new BulkReads(hardwareMap);

        panelsField.setOffsets(PanelsField.INSTANCE.getPresets().getPEDRO_PATHING());

        // Which robot is this? Read once, from the hub network name (see RobotIdentity).
        robotId = RobotIdentity.resolve();
        idBanner = robotId.banner();
        // No setDisplayFormat here any more: nothing this OpMode sends to the Driver Hub contains
        // HTML tags now that the identity banner has moved to Panels.

        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry); // fires once every 14 days, silent otherwise

        drivetrain = new Drivetrain(hardwareMap);
        intake = new Intake(hardwareMap);
        driver = new GamepadEx(gamepad1);

        // RIGHT TRIGGER = hold to run the intake, release to stop.
        //
        // Trigger, not getGamepadButton: the SDK reports a trigger as an analog 0..1 value, not a
        // button, so there is no button to bind. The Trigger wrapper turns "squeezed past the
        // threshold" into the press/release edges the binding needs. The threshold is live-tunable
        // (§6 Tier 1) — the lambda reads the static each time, so turning it in Panels takes effect
        // without a redeploy.
        //
        // whileActiveOnce starts the command on the squeeze and cancels it on the release, and does
        // NOT restart it in between. whileActiveContinuous would re-schedule the command every loop,
        // restarting its timeout forever and defeating the safety net in IntakeCommand.
        //
        // IvyOpMode polls this binding inside super.run() below (framework/Trigger). No per-loop
        // allocation: the lambda and the command are both built once, here at init (§4 rule 8).
        new Trigger(() -> driver.getTrigger(GamepadKeys.Trigger.RIGHT_TRIGGER)
                > Intake.triggerThreshold)
                .whileActiveOnce(new IntakeCommand(intake));

        // Pedro drives the wheels; startTeleopDrive() sets it to open-loop mode (§10).
        // The identity picks this robot's own Pedro tuning — comp and test drive differently.
        follower = Constants.createFollower(hardwareMap, robotId);

        // Fail loud rather than let two controllers fight over the same motors. Pedro's hold
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

        // FIELD-CENTRIC: rotate the stick inputs by the robot's heading, so "push forward" means
        // "drive away from the driver" whichever way the robot is pointing. Pedro 2 did this inside
        // setTeleOpDrive; Pedro 3 makes it an explicit call that hands back the wheel powers.
        DrivePowers powers = ManualDrive.fieldCentric(
                forward * cap, strafe * cap, turn * cap, follower.pose().heading());

        // STAND YOUR GROUND. Let go of the sticks and the robot braces on the spot instead of
        // coasting, so a push does not move us. Touch a stick and it hands control straight back.
        //
        // This was our own util until 2026-09-22; Pedro 3.0.1 ships it as driveOrHold. Its rule is
        // better than ours was: it waits until the robot is actually SLOW before grabbing the pose,
        // where we guessed at a fixed delay to ride out the coast.
        if (Drivetrain.holdWhenIdleEnabled) {
            ManualDrive.driveOrHold(follower, powers,
                    Drivetrain.holdInputThreshold, Drivetrain.holdVelocityThreshold);
        } else {
            follower.manual(powers);
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
        //   (§3 allows a local state machine for a genuine mode), capturing follower.pose() only on
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

        // Runs every subsystem's periodic(), the trigger bindings, then Ivy's scheduler.
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
        panels.debug(idBanner);
        // THE DRIVER HUB SHOWS THESE FOUR LINES AND NOTHING ELSE. Cut to this set by Aaron on
        // 2026-09-23. Everything that used to sit here still exists — it moved to Panels, which is
        // where §4 rule 6 says heavy data belongs — EXCEPT the three noted below, which are gone
        // from the Driver Hub on purpose:
        //   - the robot identity banner. Still on Panels (panels.debug above) and in every
        //     snapshot, so you can always tell which robot produced a run — but a DRIVER can no
        //     longer see comp-vs-test at a glance (§10). Know that before a match.
        //   - drive mode and intake state, which §8 lists as Driver Hub items.
        //   - "Worst ms". §4 rule 7 asks for ms AND Hz; only Hz is here now. Loop time is still
        //     measured every loop, so nothing stopped watching — only the readout is shorter.
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("X in", follower.pose().x());
        telemetry.addData("Y in", follower.pose().y());
        telemetry.addData("Heading °", Math.toDegrees(follower.pose().heading()));

        // Drive current, per motor, right now. Four wheels side by side is the view that shows a
        // single motor working harder than its three neighbours — a dragging bearing, a jammed
        // wheel, a wire about to let go. A total would average that away.
        //
        // Off in Panels (Drivetrain.currentMonitorEnabled) removes both the readouts and the four
        // hub round-trips behind them — which are not free, so watch Loop Hz above after turning it
        // on. Drivetrain.getAmpReadMs() times those reads if you want the cost itemised.
        //
        // These go to PANELS ONLY now, not the Driver Hub. §5 still requires per-wheel drive
        // telemetry and this is still it — §4 rule 6 just wants it on the dev dashboard rather than
        // in front of a driver mid-match.
        if (Drivetrain.currentMonitorEnabled) {
            addAmps("LF A", drivetrain.getLfAmps());
            addAmps("LR A", drivetrain.getLrAmps());
            addAmps("RF A", drivetrain.getRfAmps());
            addAmps("RR A", drivetrain.getRrAmps());
            // The sum, for battery load at a glance. Already computed from the same four readings,
            // so this costs no extra hub traffic.
            addAmps("Total A", drivetrain.getTotalAmps());
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
        drawRobot(follower.pose());
        panelsField.update();
    }

    /** Draws the robot as a circle at pose, with a line showing heading (mirrors pedroPathing/Tuning.java's Drawing). */
    private static void drawRobot(Pose pose) {
        panelsField.setStyle(robotLook);
        panelsField.moveCursor(pose.x(), pose.y());
        panelsField.circle(ROBOT_RADIUS);

        // Heading line. Pedro 3 dropped getHeadingAsUnitVector(), so the unit vector is built here
        // from the heading itself — the same two numbers, one less API to depend on.
        double dx = Math.cos(pose.heading()) * ROBOT_RADIUS;
        double dy = Math.sin(pose.heading()) * ROBOT_RADIUS;
        panelsField.setStyle(robotLook);
        panelsField.moveCursor(pose.x() + dx / 2, pose.y() + dy / 2);
        panelsField.line(pose.x() + dx, pose.y() + dy);
    }


    /**
     * Sends one amp reading to both displays, to two decimals.
     *
     * This formats a string every loop, which §4 rule 8 tells you to avoid — and it is the right
     * call here anyway. Reading these four numbers costs four blocking round-trips to the hub,
     * milliseconds; formatting them costs microseconds. The expensive half is the reads, and both
     * halves vanish together when you turn the monitor off. Formatting once and handing the same
     * string to both displays keeps it to one allocation per motor rather than two.
     */
    private void addAmps(String caption, double amps) {
        // Panels only — the Driver Hub set is the four lines in the telemetry block above.
        panels.addData(caption, String.format(Locale.US, "%.2f", amps));
    }

    /** Returns 0 if |value| is within the deadzone, otherwise passes value through unchanged. */
    private static double applyDeadzone(double value, double deadzone) {
        return Math.abs(value) < deadzone ? 0.0 : value;
    }

    @Override
    public void reset() {
        follower.stop(); // drop any hold before the OpMode ends
        drivetrain.stop();
        intake.stop(); // never leave the intake spinning after the OpMode ends
        Persistence.saveTuning(robotId);
        Persistence.Snapshot stopSnap = new Persistence.Snapshot();
        stopSnap.robot = robotId.robot.name();
        stopSnap.networkName = robotId.networkName;
        stopSnap.startingBatteryVolts = startBatteryVolts;
        stopSnap.captureLoop(loopTimer); // loop-time trend data (§0)
        Persistence.writeSnapshot(stopSnap, hardwareMap); // post-match record (section 7)
        super.reset(); // clears Ivy's scheduler, subsystems and triggers
    }
}
