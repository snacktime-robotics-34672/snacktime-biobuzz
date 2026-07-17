package org.firstinspires.ftc.teamcode.opmodes;

import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;
import com.seattlesolvers.solverslib.gamepad.GamepadEx;
import com.seattlesolvers.solverslib.gamepad.GamepadKeys;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;

/**
 * TeleOpExample — gamepad drives the drivetrain; buttons trigger intake commands (CLAUDE.md section 3).
 *
 * Driver-facing telemetry stays MINIMAL and glanceable (sections 4 and 8) — this is the in-match
 * Driver Hub view; heavy debugging data goes to Panels only.
 *
 * NOTE: confirm the SolversLib bindings API against your installed version. The pattern (bind a
 * button edge to a command) is stable and matches FTCLib/WPILib.
 */
@TeleOp(name = "34672 TeleOp (example)")
public class TeleOpExample extends CommandOpMode {

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Drivetrain drivetrain;
    private Intake intake;
    private GamepadEx driver;

    @Override
    public void initialize() {
        // MANUAL bulk caching — the biggest lever on loop time (section 0, section 4 rule 1).
        bulkReads = new BulkReads(hardwareMap);

        drivetrain = new Drivetrain(hardwareMap);
        intake = new Intake(hardwareMap);
        driver = new GamepadEx(gamepad1);

        // Button bindings -> commands (section 3).
        driver.getGamepadButton(GamepadKeys.Button.A).whenPressed(intake.intakeCommand());
        driver.getGamepadButton(GamepadKeys.Button.B).whenPressed(intake.ejectCommand());
        driver.getGamepadButton(GamepadKeys.Button.X).whenPressed(intake.stopCommand());

        Persistence.writeSnapshot(new Persistence.Snapshot()); // safe: init, not the loop (section 7)
        loopTimer.reset();
    }

    @Override
    public void run() {
        // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (section 4).
        bulkReads.clear();

        // Read -> process -> write (section 4, rule 2).
        double cap = driver.getButton(GamepadKeys.Button.LEFT_BUMPER)
                ? TuningConfig.driveSlowModeCap
                : TuningConfig.driveSpeedCap;

        drivetrain.driveRobot(
                -driver.getLeftY(),   // forward
                driver.getLeftX(),    // strafe
                driver.getRightX(),   // turn
                cap);

        // Runs the command scheduler + every subsystem's periodic().
        super.run();

        // Loop-time readout is REQUIRED (section 0 prime directive, section 4 rule 7).
        // Pass numbers, not hand-built strings (rule 8). Watch Loop Hz for regressions.
        loopTimer.update();
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());

        // Minimal driver telemetry (Driver Hub). Only what a driver glances at.
        telemetry.addData("Intake", intake.getMode());
        telemetry.update();
    }

    @Override
    public void reset() {
        intake.stop();
        drivetrain.stop();
        Persistence.writeSnapshot(new Persistence.Snapshot()); // post-match record (section 7)
        CommandScheduler.getInstance().reset();
    }
}
