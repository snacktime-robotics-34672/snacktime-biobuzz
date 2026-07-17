package org.firstinspires.ftc.teamcode.commands;

import com.pedropathing.follower.Follower;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.PathChain;
import com.seattlesolvers.solverslib.command.CommandBase;

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
 * CREDIT: Ported verbatim from decode-2025 (common/commands/FollowPathCommand.java), which itself
 * credits Powercube from Watt-sUP 16166 — the community-standard wrapper for Pedro+SolversLib.
 * See also: https://github.com/FTC-23511/SolversLib/blob/master/examples/src/main/java/org/firstinspires/ftc/teamcode/PedroCommandSample/FollowPedroSample.java
 */
public class FollowPathCommand extends CommandBase {

    private final Follower follower;
    private final PathChain path;
    private boolean holdEnd;
    private double maxPower;

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

    @Override
    public void initialize() {
        follower.setMaxPower(maxPower);
        follower.followPath(path, holdEnd);
    }

    @Override
    public boolean isFinished() {
        return !follower.isBusy();
    }
}