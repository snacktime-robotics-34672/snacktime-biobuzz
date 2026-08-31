package org.firstinspires.ftc.teamcode.commands;

import com.pedropathing.follower.Follower;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.PathChain;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.command.CommandBase;

import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;

/**
 * FollowPathCommand — wraps a Pedro Path or PathChain as a SolversLib CommandBase so it can be
 * composed inside a command tree (SequentialCommandGroup, ParallelCommandGroup, etc.) instead of
 * hand-rolled as a state machine.
 *
 * This is the missing glue between Pedro's imperative `follower.followPath(...)` API and
 * SolversLib's command-based auto structure. With it, autos read top-to-bottom as a plan:
 *
 *   schedule(new SequentialCommandGroup(
 *       new FollowPathCommand(follower, pickupPath),
 *       intake.grabCommand(),
 *       new FollowPathCommand(follower, deliverPath),
 *       intake.releaseCommand()
 *   ));
 *
 * CREDIT: Ported from decode-2025 (common/commands/FollowPathCommand.java), which itself credits
 * Powercube from Watt-sUP 16166 — the community-standard wrapper for Pedro+SolversLib.
 * See also: https://github.com/FTC-23511/SolversLib/blob/master/examples/src/main/java/org/firstinspires/ftc/teamcode/PedroCommandSample/FollowPedroSample.java
 *
 * WE DIVERGE FROM UPSTREAM IN ONE WAY: this command has a built-in timeout. The original finishes
 * only when {@code follower.isBusy()} goes false, so a path that never completes — a stall against a
 * wall, a bad pose estimate, a robot wedged on another — hangs the command tree for the rest of the
 * match. CLAUDE.md §5 does not allow that. SolversLib does offer {@code .withTimeout(ms)} on every
 * command, but a safety net you have to remember to attach is one you will forget on the path that
 * needed it, so it is built in here and on by default.
 */
public class FollowPathCommand extends CommandBase {

    private final Follower follower;
    private final PathChain path;
    private boolean holdEnd;
    private double maxPower;

    /** < 0 means "use the live default from Drivetrain". Set per-path with {@link #setTimeout}. */
    private double timeoutSeconds = -1;
    private final ElapsedTime timer = new ElapsedTime();
    private boolean timedOut;

    public FollowPathCommand(Follower follower, PathChain path) {
        this(follower, path, true, 1.0);
    }

    public FollowPathCommand(Follower follower, PathChain path, boolean holdEnd) {
        this(follower, path, holdEnd, 1.0);
    }

    public FollowPathCommand(Follower follower, PathChain path, double maxPower) {
        this(follower, path, true, maxPower);
    }

    public FollowPathCommand(Follower follower, PathChain path, boolean holdEnd, double maxPower) {
        this.follower = follower;
        this.path = path;
        this.holdEnd = holdEnd;
        this.maxPower = maxPower;
    }

    public FollowPathCommand(Follower follower, Path path) {
        this(follower, path, true, 1.0);
    }

    public FollowPathCommand(Follower follower, Path path, boolean holdEnd) {
        this(follower, path, holdEnd, 1.0);
    }

    public FollowPathCommand(Follower follower, Path path, double maxPower) {
        this(follower, path, true, maxPower);
    }

    public FollowPathCommand(Follower follower, Path path, boolean holdEnd, double maxPower) {
        this.follower = follower;
        this.path = new PathChain(path);
        this.holdEnd = holdEnd;
        this.maxPower = maxPower;
    }

    /** @param holdEnd whether the robot should maintain its ending pose after the path completes */
    public FollowPathCommand setHoldEnd(boolean holdEnd) {
        this.holdEnd = holdEnd;
        return this;
    }

    /** @param maxPower 0..1 cap on drive power during the follow */
    public FollowPathCommand setMaxPower(double maxPower) {
        this.maxPower = maxPower;
        return this;
    }

    /**
     * Overrides the live default from {@link Drivetrain#followPathTimeoutSec} for this path. Raise
     * it for a long multi-segment route; a whole PathChain runs under one timeout.
     */
    public FollowPathCommand setTimeout(double seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    @Override
    public void initialize() {
        timer.reset();
        timedOut = false;
        follower.setMaxPower(maxPower);
        follower.followPath(path, holdEnd);
    }

    @Override
    public boolean isFinished() {
        if (timer.seconds() >= effectiveTimeout()) {
            timedOut = true;
            RobotLog.ww("FollowPath", "TIMEOUT after %.1fs — path did not finish. Robot stopped at "
                            + "(%.1f, %.1f). Raise the timeout if this path is legitimately long.",
                    timer.seconds(), follower.getPose().getX(), follower.getPose().getY());
            return true;
        }
        return !follower.isBusy();
    }

    @Override
    public void end(boolean interrupted) {
        // Stop driving a path we have given up on. A clean finish with holdEnd set leaves Pedro
        // holding the end point, which must not be disturbed.
        if (interrupted || timedOut) {
            follower.breakFollowing();
        }
    }

    private double effectiveTimeout() {
        return timeoutSeconds >= 0 ? timeoutSeconds : Drivetrain.followPathTimeoutSec;
    }
}