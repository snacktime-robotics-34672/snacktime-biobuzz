package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.commands.Commands;
import com.seattlesolvers.solverslib.hardware.motors.Motor;
import com.seattlesolvers.solverslib.hardware.motors.MotorEx;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.framework.Subsystem;
import org.firstinspires.ftc.teamcode.logic.IntakeLogic;

/**
 * Intake — the two motors that pull game pieces in.
 *
 * WHAT IT OWNS: both intake motors, L_INTAKE and R_INTAKE, and nothing else. Because the scheduler
 * knows this subsystem owns them, two commands can never fight over the same motor (CLAUDE.md §3).
 *
 * WHY ONE SUBSYSTEM AND NOT TWO: the two motors are one mechanism. They always start together, stop
 * together, and run at the same speed — nothing ever wants the left roller without the right one.
 * Splitting them into two subsystems would let a command claim half an intake, which is a fault we
 * would have to guard against rather than a capability we want.
 *
 * WHICH WAY THEY SPIN: most two-sided intakes counter-rotate — the rollers turn toward each other to
 * pull a piece in, so the two motors run opposite directions. {@link #rightInverted} handles that and
 * it is a LIVE flag: if the intake spits pieces out instead of pulling them in, flip it in Panels and
 * try again, no deploy. See the bench procedure on that field.
 *
 * WHAT IT EXPOSES: intent, not power — {@link #intake()} and {@link #stop()}. The OpMode says what
 * it wants; how hard the motors run is a tunable in this file, turned in Panels while the robot is
 * running (§6 Tier 1).
 *
 * HOW TO TELL IF IT IS WORKING: the Driver Hub shows "Intake" as ON or off, and Panels shows the
 * commanded power when verboseTelemetry is on. The intake does NOT read motor current — the rollers
 * are watched by eye and ear at the bench, and a current read is a blocking round-trip to the
 * Expansion Hub that the loop should not pay for (§0). Add it back only if a fault turns up that
 * cannot be seen any other way.
 *
 * LOOP COST: two motor writes per loop while the intake runs, and NOTHING at all when it is stopped
 * (see {@link #setPower(double)} — repeat writes of the same value are skipped).
 */
@Configurable
public class Intake extends Subsystem {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------

    /**
     * Power the intake runs at while the trigger is held, -1..1. Both motors run at this speed.
     * Start low and raise it until pieces feed cleanly — a too-fast intake spits pieces back out as
     * often as a too-slow one fails to grab them. Negative runs BOTH motors the other way, which
     * ejects; it is also the quick fix if the whole intake turns out to be wired backwards.
     */
    public static double intakePower = 0.9;

    /**
     * Does the right motor run opposite the left one? True for a normal counter-rotating intake,
     * where the two rollers turn toward each other to pull a piece in.
     *
     * BENCH PROCEDURE — get this right before tuning anything else:
     *   1. Hold the trigger with no game piece and watch the rollers.
     *   2. Both rollers should pull INWARD, toward the middle of the robot.
     *   3. If they fight each other (one in, one out), flip this in Panels.
     *   4. If both push OUTWARD together, this flag is right but the whole intake is backwards —
     *      make {@code intakePower} negative instead.
     *
     * This is a live flag rather than a motor direction set once at init, on purpose: a direction
     * set at init only changes with a redeploy, and this is exactly the number you want to try both
     * ways in ten seconds on the bench.
     */
    public static boolean rightInverted = true;

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

    // ---- Hardware ---------------------------------------------------------------------------

    /**
     * Config names must match the Robot Controller configuration on BOTH robots (§10).
     *
     * BOTH MOTORS LIVE ON THE EXPANSION HUB — L_INTAKE on port 0, R_INTAKE on port 1. That matters
     * for loop time (§0): every write to them crosses the RS485 link to the second hub, which costs
     * more than a write to a Control Hub port. It is why {@link #setPower(double)} skips writes that
     * would not change anything. Bulk reads need nothing special — util/BulkReads puts EVERY hub in
     * MANUAL mode and clears them all.
     */
    public static final String LEFT_MOTOR_NAME = "L_INTAKE";
    public static final String RIGHT_MOTOR_NAME = "R_INTAKE";

    private final MotorEx left;
    private final MotorEx right;

    /**
     * What we last told the LEFT motor to do — the mechanism's power, before the right motor's
     * inversion. Read by telemetry; also used to skip repeat writes.
     */
    private double commandedPower = 0.0;

    /** Whether the last write used rightInverted, so a live flip is noticed and re-written. */
    private boolean lastRightInverted = rightInverted;

    public Intake(HardwareMap hardwareMap) {
        // Throws at init if either motor is missing from the hub configuration — deliberate. A
        // half-present intake must stop the OpMode on the bench, not surface as one dead roller
        // mid-match (§5 deterministic init, fail loud).
        left = new MotorEx(hardwareMap, LEFT_MOTOR_NAME);
        right = new MotorEx(hardwareMap, RIGHT_MOTOR_NAME);

        // BRAKE, so releasing the trigger stops the rollers now instead of letting them coast a
        // piece further in. Change to FLOAT if the intake needs to spin down gently.
        left.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
        right.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);

        left.set(0.0);
        right.set(0.0);
    }

    // ---- Intent-level methods ---------------------------------------------------------------

    /** Runs both rollers at the tuned power, capped by {@link #maxPower}. */
    public void intake() {
        setPower(IntakeLogic.clamp(intakePower, maxPower));
    }

    /** Stops both rollers. Safe to call repeatedly. */
    public void stop() {
        setPower(0.0);
    }

    /**
     * Sends power to both motors, clamped to the safety cap. The right motor gets the opposite sign
     * when {@link #rightInverted} is set.
     *
     * Repeat writes of the same value are skipped. A motor write is a hub round-trip and there are
     * two of them here, while an intake spends most of a match sitting at one value — usually zero
     * — so this keeps the prime directive's budget (§0) for the loops that need it. Flipping
     * rightInverted in Panels also counts as a change, so a live flip takes effect on the next loop
     * rather than waiting for the power to change.
     */
    public void setPower(double power) {
        double safe = IntakeLogic.clamp(power, maxPower);
        if (safe == commandedPower && rightInverted == lastRightInverted) return;
        commandedPower = safe;
        lastRightInverted = rightInverted;
        left.set(safe);
        right.set(IntakeLogic.sidePower(safe, rightInverted));
    }

    /** True while the intake is being driven. What the Driver Hub shows (§8). */
    public boolean isRunning() {
        return commandedPower != 0.0;
    }

    /** The mechanism power last commanded — what the LEFT motor was sent. */
    public double getCommandedPower() {
        return commandedPower;
    }

    // ---- Command wrappers -------------------------------------------------------------------

    /**
     * Stops the intake, as a command an auto tree can hold. Running the intake is its own command
     * class — see {@code commands/IntakeCommand} — because running needs a timeout and stopping
     * does not.
     */
    public Command stopCommand() {
        return Commands.instant(this::stop).requiring(this);
    }

    @Override
    public void periodic() {
        // Bench detail, off during matches (§4 rule 8). Numbers, not built strings.
        if (TuningConfig.verboseTelemetry) {
            TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();
            panels.addData("intake power", commandedPower);
        }
    }
}
