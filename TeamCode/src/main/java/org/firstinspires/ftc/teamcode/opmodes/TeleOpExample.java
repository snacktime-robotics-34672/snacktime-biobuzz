package org.firstinspires.ftc.teamcode.opmodes;

import com.pedropathing.follower.Follower;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;
import com.seattlesolvers.solverslib.gamepad.GamepadEx;
import com.seattlesolvers.solverslib.gamepad.GamepadKeys;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.pedroPathing.Constants;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;

/**
 * TeleOpExample — field-centric mecanum drive. LEFT_BUMPER = slow mode.
 *
 * Pedro reads the Pinpoint heading and rotates stick inputs to field coordinates each loop.
 * Driver Hub telemetry is minimal and glanceable (CLAUDE.md sections 4, 8).
 */
@TeleOp(name = "34672 TeleOp (example)")
public class TeleOpExample extends CommandOpMode {

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private GamepadEx driver;
    private Follower follower;
    private double startBatteryVolts = 0.0;

    @Override
    public void initialize() {
        // MANUAL bulk caching — the biggest lever on loop time (section 0, section 4 rule 1).
        bulkReads = new BulkReads(hardwareMap);
        Persistence.loadAndApplyTuning(telemetry);

        drivetrain = new Drivetrain(hardwareMap);
        driver = new GamepadEx(gamepad1);

        // Pedro drives the wheels; startTeleopDrive() sets it to open-loop mode (§10).
        follower = Constants.createFollower(hardwareMap);
        follower.startTeleopDrive();

        Persistence.writeSnapshot(new Persistence.Snapshot(), hardwareMap); // safe: init, not the loop (section 7)
        loopTimer.reset();
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (section 4).
        bulkReads.clear();
        if (startBatteryVolts == 0.0) startBatteryVolts = Persistence.readBatteryVolts(hardwareMap);

        // Read -> process -> write (section 4, rule 2).
        double cap = driver.getButton(GamepadKeys.Button.LEFT_BUMPER)
                ? Drivetrain.driveSlowModeCap
                : Drivetrain.driveSpeedCap;

        // Field-centric: Pedro rotates strafe/forward by the Pinpoint heading before applying power.
        // Sign convention from PedroTeleOpSample: (-leftY, -leftX, -rightX).
        // TODO: if a direction is backwards on the robot, flip that sign.
        double dz = JoystickCurve.deadzone;
        double forward = applyDeadzone(-driver.getLeftY(), dz);
        double strafe  = applyDeadzone(-driver.getLeftX(), dz);
        double turn    = applyDeadzone(-driver.getRightX(), dz);
        follower.setTeleOpDrive(forward * cap, strafe * cap, turn * cap, false);
        follower.update();

        // Runs the command scheduler + every subsystem's periodic().
        super.run();

        // Loop-time readout is REQUIRED (section 0 prime directive, section 4 rule 7).
        // Pass numbers, not hand-built strings (rule 8). Watch Loop Hz for regressions.
        loopTimer.update();
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());
        telemetry.addData("Heading °", Math.toDegrees(follower.getPose().getHeading()));
        telemetry.update();
    }

    /** Returns 0 if |value| is within the deadzone, otherwise passes value through unchanged. */
    private static double applyDeadzone(double value, double deadzone) {
        return Math.abs(value) < deadzone ? 0.0 : value;
    }

    @Override
    public void reset() {
        follower.breakFollowing();
        drivetrain.stop();
        Persistence.saveTuning();
        Persistence.Snapshot stopSnap = new Persistence.Snapshot();
        stopSnap.startingBatteryVolts = startBatteryVolts;
        Persistence.writeSnapshot(stopSnap, hardwareMap); // post-match record (section 7)
        CommandScheduler.getInstance().reset();
    }
}
