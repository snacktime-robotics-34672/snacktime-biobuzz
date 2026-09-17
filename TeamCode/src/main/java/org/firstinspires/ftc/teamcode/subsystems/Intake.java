package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.seattlesolvers.solverslib.command.InstantCommand;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.hardware.motors.Motor;
import com.seattlesolvers.solverslib.hardware.motors.MotorEx;

import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.logic.IntakeLogic;

/**
 * Intake — the one motor that pulls game pieces in.
 *
 * WHAT IT OWNS: the intake motor, and nothing else. Because the scheduler knows this subsystem owns
 * that motor, two commands can never fight over it (CLAUDE.md §3).
 *
 * WHAT IT EXPOSES: intent, not power — {@link #intake()} and {@link #stop()}. The OpMode says what
 * it wants; how hard the motor runs is a tunable in this file, turned in Panels while the robot is
 * running (§6 Tier 1).
 *
 * HOW TO TELL IF IT IS WORKING: the Driver Hub shows "Intake" as ON or off. At the bench, turn on
 * {@code currentMonitorEnabled} in Panels and watch the amps — a jammed intake pulls hard current
 * while the power number says it should be spinning freely.
 *
 * LOOP COST: one motor write per loop while the intake runs, and nothing at all when it is stopped
 * (see {@link #setPower(double)} — repeat writes of the same value are skipped). The current read
 * is the one real cost here and it is OFF by default, same reasoning as the drive current monitor.
 */
@Configurable
public class Intake extends SubsystemBase {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------

    /**
     * Power the intake runs at while the trigger is held, -1..1. Start low and raise it until
     * pieces feed cleanly — a too-fast intake spits pieces back out as often as a too-slow one
     * fails to grab them. Negative runs the motor the other way, which is the quick fix if the
     * intake turns out to be wired backwards.
     */
    public static double intakePower = 0.8;

    /**
     * How far the trigger must be squeezed before the intake turns on, 0..1. This is a dead band,
     * not a delay: a trigger at rest does not read exactly 0.0, and without this the intake could
     * run by itself from the moment the match starts. Raise it if a resting trigger starts the
     * intake; lower it if the driver has to squeeze uncomfortably hard.
     */
    public static double triggerThreshold = 0.25;

    /**
     * Hard cap on intake power magnitude, 0..1 (§5 actuator safety limits). {@code intakePower} can
     * never get past this, so a slipped decimal point in the dashboard cannot send the intake to
     * full power. Set it to 0 to disable the intake entirely without touching any binding.
     */
    public static double maxPower = 1.0;

    /**
     * Safety timeout for IntakeCommand, in seconds. Nothing may run for a whole match (§5). Holding
     * the trigger longer than this stops the intake and logs it loudly; release and squeeze again
     * to restart. Raise it if a legitimate intake run keeps tripping it.
     */
    public static double intakeTimeoutSec = 15.0;

    /**
     * Watch the intake motor's current draw. OFF by default because it costs loop time: motor
     * current is NOT part of the bulk read, so each reading is a blocking round-trip to the hub
     * (the same reason Drivetrain.currentMonitorEnabled defaults off). Turn it on at the bench when
     * you are chasing a jam or a weak intake, then turn it back off.
     */
    public static boolean currentMonitorEnabled = false;

    // ---- Hardware ---------------------------------------------------------------------------

    /** Config name must match the Robot Controller configuration on BOTH robots (§10). */
    public static final String MOTOR_NAME = "intake_motor";

    private final MotorEx motor;

    /** What we last told the motor to do. Read by telemetry; also used to skip repeat writes. */
    private double commandedPower = 0.0;

    /** Most recent current reading, amps. Stays 0 while the monitor is off. */
    private double amps = 0.0;

    public Intake(HardwareMap hardwareMap) {
        // Throws at init if the motor is missing from the hub configuration — deliberate. A missing
        // intake must stop the OpMode on the bench, not surface as a dead mechanism mid-match
        // (§5 deterministic init, fail loud).
        motor = new MotorEx(hardwareMap, MOTOR_NAME);

        // BRAKE, so releasing the trigger stops the rollers now instead of letting them coast a
        // piece further in. Change to FLOAT if the intake needs to spin down gently.
        motor.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
        motor.set(0.0);
    }

    // ---- Intent-level methods ---------------------------------------------------------------

    /** Runs the intake at the tuned power, capped by {@link #maxPower}. */
    public void intake() {
        setPower(IntakeLogic.clamp(intakePower, maxPower));
    }

    /** Stops the intake. Safe to call repeatedly. */
    public void stop() {
        setPower(0.0);
    }

    /**
     * Sends power to the motor, clamped to the safety cap.
     *
     * Repeat writes of the same value are skipped. A motor write is a hub round-trip, and an intake
     * spends most of a match sitting at one value — usually zero — so this keeps the prime
     * directive's budget (§0) for the loops that need it.
     */
    public void setPower(double power) {
        double safe = IntakeLogic.clamp(power, maxPower);
        if (safe == commandedPower) return;
        commandedPower = safe;
        motor.set(safe);
    }

    /** True while the intake is being driven. What the Driver Hub shows (§8). */
    public boolean isRunning() {
        return commandedPower != 0.0;
    }

    /** The power last sent to the motor. */
    public double getCommandedPower() {
        return commandedPower;
    }

    /** Most recent current reading in amps, or 0 while {@link #currentMonitorEnabled} is off. */
    public double getAmps() {
        return amps;
    }

    // ---- Command wrappers -------------------------------------------------------------------

    /**
     * Stops the intake, as a command an auto tree can hold. Running the intake is its own command
     * class — see {@code commands/IntakeCommand} — because running needs a timeout and stopping
     * does not.
     */
    public InstantCommand stopCommand() {
        return new InstantCommand(this::stop, this);
    }

    @Override
    public void periodic() {
        // One hub round-trip, only when someone asked for it (see currentMonitorEnabled).
        if (currentMonitorEnabled) {
            amps = motor.getCurrent(CurrentUnit.AMPS);
        }

        // Bench detail, off during matches (§4 rule 8). Numbers, not built strings.
        if (TuningConfig.verboseTelemetry) {
            TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();
            panels.addData("intake power", commandedPower);
            if (currentMonitorEnabled) panels.addData("intake amps", amps);
        }
    }
}
