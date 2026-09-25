package org.firstinspires.ftc.teamcode.framework;

import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.behaviors.BlockedBehavior;
import com.pedropathing.ivy.behaviors.ConflictBehavior;
import com.pedropathing.ivy.behaviors.EndCondition;
import com.pedropathing.ivy.behaviors.InterruptedBehavior;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.config.TuningConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * TeamCommand — base class for our own command classes (FollowPathCommand, IntakeCommand ...).
 *
 * WHY NOT BUILD EVERY COMMAND WITH IVY'S {@code Command.build()}: that is fine for a one-liner (see
 * Intake.stopCommand()), but a command with state and a timeout reads much more clearly as a class
 * with four named methods. This class gives Ivy's Command interface those four methods:
 *
 *   Ivy calls        we implement
 *   start()      →   initialize()
 *   execute()    →   execute()
 *   done()       →   isFinished()
 *   end(cond)    →   end(interrupted)   — interrupted is true for anything but a natural finish
 *
 * So a TeamCommand is a normal Ivy command: it goes straight into Ivy's sequential/parallel/race
 * groups, and {@code .then(...)}, {@code .with(...)}, {@code .until(...)} all work on it.
 *
 * REQUIREMENTS: call {@link #addRequirements} with each subsystem the command drives. Ivy then stops
 * two commands from driving the same motor at once: a new command that needs a busy subsystem
 * interrupts the old one (Ivy's default OVERRIDE behaviour — same as SolversLib's).
 *
 * TIMEOUTS are still each subclass's job, built in (CLAUDE.md §5, §9). This base does not add one,
 * because each command wants its own live default and its own log message on timeout.
 *
 * LOGGING: with TuningConfig.verboseTelemetry on, every start and end is logged to the RC log. This
 * replaces the scheduler hooks SolversLib had. It runs on start/end only, never per loop.
 */
public abstract class TeamCommand implements Command {

    private final Set<Object> requirements = new HashSet<>();

    /** Declares the subsystems this command drives. Call it from the constructor. */
    public final void addRequirements(Object... subsystems) {
        Collections.addAll(requirements, subsystems);
    }

    // ---- The four methods a subclass writes ----------------------------------------------------

    /** Runs once when the command starts. */
    public void initialize() {
    }

    /** Runs every loop while the command is running. */
    @Override
    public void execute() {
    }

    /** True when the command is done. Checked every loop, right after execute(). */
    public boolean isFinished() {
        return false;
    }

    /** Runs once when the command stops, for any reason. */
    public void end(boolean interrupted) {
    }

    // ---- Ivy's Command interface, mapped onto the four methods above ---------------------------

    @Override
    public final void start() {
        if (TuningConfig.verboseTelemetry) {
            RobotLog.i("Command started: %s", getClass().getSimpleName());
        }
        initialize();
    }

    @Override
    public final boolean done() {
        return isFinished();
    }

    @Override
    public final void end(EndCondition endCondition) {
        boolean interrupted = endCondition != EndCondition.NATURALLY;
        if (TuningConfig.verboseTelemetry) {
            RobotLog.i("Command %s: %s", interrupted ? "interrupted" : "finished",
                    getClass().getSimpleName());
        }
        end(interrupted);
    }

    @Override
    public final Set<Object> requirements() {
        return requirements;
    }

    // Ivy's defaults, stated here so a reader does not have to go and look them up. They match how
    // SolversLib behaved: equal priority, and a newer command takes the subsystem from an older one.

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public InterruptedBehavior interruptedBehavior() {
        return InterruptedBehavior.END;
    }

    @Override
    public ConflictBehavior conflictBehavior() {
        return ConflictBehavior.OVERRIDE;
    }

    @Override
    public BlockedBehavior blockedBehavior() {
        return BlockedBehavior.CANCEL;
    }
}
