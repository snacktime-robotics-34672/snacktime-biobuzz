package org.firstinspires.ftc.teamcode.commands;

import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.framework.TeamCommand;
import org.firstinspires.ftc.teamcode.subsystems.Intake;

/**
 * IntakeCommand — runs the intake until something stops it.
 *
 * IN TELEOP: bound to the right trigger as hold-to-run. Squeeze past the threshold and this command
 * starts; release and the binding cancels it, which stops the motor in {@link #end(boolean)}. The
 * driver never has to remember to turn the intake off.
 *
 * IN AUTO: give it a time and drop it in a tree, where it doubles as "run the intake for N seconds".
 *
 *   sequential(
 *       new DriveToPoseCommand(follower, PIECE_POSE),
 *       new IntakeCommand(intake).setTimeout(2.0)
 *   );
 *
 * TIMEOUT IS BUILT IN, not left to the caller (§5, §9): a command with no timeout can hold the
 * intake for a whole match if a trigger sticks or a binding is cancelled without ending. The
 * default is {@link Intake#intakeTimeoutSec}, live-tunable, and reaching it is logged loudly —
 * a timeout means something went wrong and should never pass silently.
 *
 * REQUIREMENTS: this command claims the Intake, so the scheduler stops anything else from driving
 * the same motor at the same time (§3).
 */
public class IntakeCommand extends TeamCommand {

    private final Intake intake;
    private final ElapsedTime timer = new ElapsedTime();

    /** < 0 means "use the live default from Intake.intakeTimeoutSec". */
    private double timeoutSeconds = -1;

    public IntakeCommand(Intake intake) {
        this.intake = intake;
        addRequirements(intake);
    }

    /** Overrides the live default timeout for this one use. Seconds. */
    public IntakeCommand setTimeout(double seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    @Override
    public void initialize() {
        timer.reset();
        intake.intake();
    }

    @Override
    public void execute() {
        // Re-assert the power every loop so a live edit of intakePower in Panels takes effect while
        // the trigger is still held — otherwise you would have to release and squeeze again to see
        // what a new number feels like, which is the whole point of Tier 1 tuning (§6).
        // The subsystem skips the hub write when the value has not changed, so this is free.
        intake.intake();
    }

    @Override
    public boolean isFinished() {
        if (timer.seconds() >= effectiveTimeout()) {
            RobotLog.ww("Intake", "TIMEOUT after %.1fs — intake stopped. Release the trigger and "
                    + "squeeze again to restart, or raise Intake.intakeTimeoutSec.", timer.seconds());
            return true;
        }
        return false;
    }

    @Override
    public void end(boolean interrupted) {
        // Runs on every exit — trigger released, timeout, or the scheduler handing the motor to
        // another command. The intake never keeps running by accident.
        intake.stop();
    }

    private double effectiveTimeout() {
        return timeoutSeconds >= 0 ? timeoutSeconds : Intake.intakeTimeoutSec;
    }
}
