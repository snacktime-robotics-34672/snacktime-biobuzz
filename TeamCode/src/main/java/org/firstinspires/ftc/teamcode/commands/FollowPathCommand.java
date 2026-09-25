package org.firstinspires.ftc.teamcode.commands;

import com.pedropathing.follower.Follower;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.framework.TeamCommand;
import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;

/**
 * FollowPathCommand — follows one Pedro Path as an Ivy command, so it can sit inside a command tree
 * (Ivy's sequential / parallel / race groups) instead of a hand-rolled state machine. With it, autos
 * read top-to-bottom as a plan:
 *
 *   schedule(sequential(
 *       new FollowPathCommand(follower, pickupPath),
 *       intake.grabCommand(),
 *       new FollowPathCommand(follower, deliverPath),
 *       intake.releaseCommand()
 *   ));
 *
 * WHY NOT IVY'S OWN {@code PedroCommands.follow(follower, path)}: it has no timeout. It finishes only
 * when {@code follower.isBusy()} goes false, so a path that never completes — a stall against a wall,
 * a bad pose estimate, a robot wedged on another — hangs the command tree for the rest of the match.
 * CLAUDE.md §5 does not allow that, and a safety net you have to remember to attach is one you will
 * forget on the path that needed it. So the timeout is built in here and on by default. Use this
 * class, not Ivy's follow(), for every path.
 *
 * CREDIT: first ported from decode-2025 (common/commands/FollowPathCommand.java), which credits
 * Powercube from Watt-sUP 16166. Moved from SolversLib to Ivy on 2026-09-24.
 */
public class FollowPathCommand extends TeamCommand {

    private final Follower follower;
    private final Path path;
    private boolean holdEnd;
    private double maxPower;

    /** < 0 means "use the live default from Drivetrain". Set per-path with {@link #setTimeout}. */
    private double timeoutSeconds = -1;
    private final ElapsedTime timer = new ElapsedTime();
    private boolean timedOut;

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
        this.path = path;
        this.holdEnd = holdEnd;
        this.maxPower = maxPower;
    }

    /** @param holdEnd whether the robot should maintain its ending pose after the path completes */
    public FollowPathCommand setHoldEnd(boolean holdEnd) {
        this.holdEnd = holdEnd;
        return this;
    }

    /**
     * @param maxPower 0..1 cap on drive power during the follow.
     * @deprecated NO LONGER APPLIED. Pedro 3 sets the speed ceiling on the follower's Foresight
     *             config ({@code maxPathSpeed}) when the follower is built, and gives no per-path
     *             override. Kept so existing call sites still compile; set the cap in
     *             {@code Constants} instead. Remove once nothing calls it.
     */
    @Deprecated
    public FollowPathCommand setMaxPower(double maxPower) {
        this.maxPower = maxPower;
        return this;
    }

    /**
     * Overrides the live default from {@link Drivetrain#followPathTimeoutSec} for this path. Raise
     * it for a long multi-segment route; a whole Path runs under one timeout.
     */
    public FollowPathCommand setTimeout(double seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    @Override
    public void initialize() {
        timer.reset();
        timedOut = false;
        follower.follow(path);
    }

    @Override
    public boolean isFinished() {
        if (timer.seconds() >= effectiveTimeout()) {
            timedOut = true;
            RobotLog.ww("FollowPath", "TIMEOUT after %.1fs — path did not finish. Robot stopped at "
                            + "(%.1f, %.1f). Raise the timeout if this path is legitimately long.",
                    timer.seconds(), follower.pose().x(), follower.pose().y());
            return true;
        }
        return !follower.isBusy();
    }

    @Override
    public void end(boolean interrupted) {
        // Stop driving a path we have given up on.
        if (interrupted || timedOut) {
            follower.stop();
            return;
        }
        // A clean finish: hold the end pose if we were asked to. Pedro 3 does not take holdEnd as a
        // follow() argument any more, so the hold is issued here, explicitly.
        if (holdEnd) {
            follower.hold(follower.pose());
        }
    }

    private double effectiveTimeout() {
        return timeoutSeconds >= 0 ? timeoutSeconds : Drivetrain.followPathTimeoutSec;
    }
}