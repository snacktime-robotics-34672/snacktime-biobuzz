package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.commands.Commands;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.framework.Subsystem;

/**
 * GameMechanism — template for a game-specific mechanism (add at kickoff).
 *
 * Pattern:
 *   - Declare hardware objects here (motors, servos, sensors for this mechanism).
 *   - Expose intent-level methods (e.g. collect(), eject(), stop()).
 *   - Wrap each in an Ivy instant command that requires this subsystem, so TeleOp can bind it to a
 *     button and Auto can compose it into a command tree.
 *   - Extends our framework Subsystem, so it registers itself and periodic() runs every loop.
 *   - periodic() publishes health telemetry gated on verboseTelemetry (CLAUDE.md §4 rule 8).
 *   - Extract any math into a pure function in logic/ so it can be unit-tested off-robot (§9).
 *   - Mechanism-specific tunables are public static fields RIGHT HERE in this file (§6 Tier 1),
 *     not in TuningConfig. @Configurable on the class makes Panels show them under "GameMechanism".
 *     Add this class to TUNING_CLASSES in Persistence.java so values survive hub restarts.
 *
 * Add config names to CLAUDE.md §10 hardware map once locked in.
 */
@Configurable
public class GameMechanism extends Subsystem {

    // TODO: add public static tunable fields here, e.g.:
    // public static double intakePower = 0.8;
    // public static double ejectPower  = -0.6;

    // TODO: declare hardware objects
    // e.g. private final MotorEx motor;
    //      private final Servo servo;

    public GameMechanism(HardwareMap hardwareMap) {
        // TODO: init hardware from hardwareMap
        // e.g. motor = new MotorEx(hardwareMap, "mechanism_motor");
    }

    // TODO: add intent-level methods for this mechanism
    // public void collect() { ... }
    // public void eject()   { ... }

    public void stop() {
        // TODO: set all outputs to safe/stopped state
    }

    // Command wrappers for button bindings and command-tree composition (CLAUDE.md §3).
    // public Command collectCommand() { return Commands.instant(this::collect).requiring(this); }
    // public Command ejectCommand()   { return Commands.instant(this::eject).requiring(this); }
    public Command stopCommand()    { return Commands.instant(this::stop).requiring(this); }

    @Override
    public void periodic() {
        if (TuningConfig.verboseTelemetry) {
            // TODO: publish mechanism health (current, position, target-vs-actual)
            // PanelsTelemetry.INSTANCE.getTelemetry().debug("mechanism state: " + ...);
        }
    }
}