package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.telemetry.PanelsTelemetry;
import com.seattlesolvers.solverslib.command.Command;
import com.seattlesolvers.solverslib.command.InstantCommand;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.hardware.motors.MotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.logic.IntakeLogic;
import org.firstinspires.ftc.teamcode.logic.IntakeLogic.Mode;

/**
 * Intake — active intake: spinning wheels driven by a SINGLE motor.
 *
 * The FLAGSHIP example subsystem. Even this simple mechanism shows every charter pattern at once:
 *   - section 3 intent-level API:  intake(), eject(), stop()  — not "setMotorPower"
 *   - section 3 local state machine: a small Mode enum (STOPPED / INTAKING / EJECTING)
 *   - section 6 configurables:    powers come from TuningConfig (live-tunable, no push)
 *   - section 8 gated telemetry:  detail published only when verboseTelemetry is on (section 4 rule 8)
 *   - section 9 pure logic:       mode -> motor power is a pure, tested function (IntakeLogic)
 *
 * Copy these patterns for every new mechanism at kickoff.
 *
 * NOTE ON API: confirm SolversLib class names against your installed version's javadocs
 * (repo.dairy.foundation/javadoc/releases/org/solverslib/core/latest). The STRUCTURE (enum +
 * configurables + intent methods + commands) is the stable part.
 */
public class Intake extends SubsystemBase {

    // Config name must match the Robot Controller config (section 10) —
    // identical on the practice and competition robots.
    private final MotorEx motor;

    private Mode mode = Mode.STOPPED;

    public Intake(HardwareMap hardwareMap) {
        motor = new MotorEx(hardwareMap, "intake_motor");
        // TODO: set motor direction so positive power pulls a game piece IN.
        // motor.setInverted(true);
        mode = Mode.STOPPED;
    }

    // -- Intent-level API ------------------------------------------------------------
    /** Pull a game piece in. */
    public void intake() { mode = Mode.INTAKING; }

    /** Push a game piece out. */
    public void eject() { mode = Mode.EJECTING; }

    /** Stop the intake. */
    public void stop() { mode = Mode.STOPPED; }

    /** What the intake is currently doing. */
    public Mode getMode() { return mode; }

    // -- Commands (what the rest of the robot composes) ------------------------------
    // Passing `this` as a requirement is what stops two commands fighting over the motor.
    public Command intakeCommand() { return new InstantCommand(this::intake, this); }
    public Command ejectCommand()  { return new InstantCommand(this::eject, this); }
    public Command stopCommand()   { return new InstantCommand(this::stop, this); }

    // -- Lifecycle -------------------------------------------------------------------
    @Override
    public void periodic() {
        // Pure, tested mapping from mode -> signed motor power (section 9).
        motor.set(IntakeLogic.powerFor(mode, TuningConfig.intakePower, TuningConfig.ejectPower));

        // Health telemetry — VERBOSE ONLY so the match loop allocates no strings
        // (prime directive section 0, section 4 rule 8).
        if (TuningConfig.verboseTelemetry) {
            PanelsTelemetry.INSTANCE.getTelemetry().debug("Intake mode: " + mode);
            PanelsTelemetry.INSTANCE.getTelemetry().debug("Intake power: " + motor.get());
        }
    }
}
