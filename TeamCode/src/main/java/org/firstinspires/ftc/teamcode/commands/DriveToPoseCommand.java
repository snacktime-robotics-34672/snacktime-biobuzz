package org.firstinspires.ftc.teamcode.commands;

import com.pedropathing.follower.Follower;
import com.pedropathing.geometry.BezierLine;
import com.pedropathing.geometry.Pose;
import com.pedropathing.paths.PathChain;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.command.CommandBase;

import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;

import java.util.function.Supplier;

/**
 * DriveToPoseCommand — drives the robot in a straight line from wherever it is now to a target pose,
 * then holds there. The command-tree counterpart to "go stand on that spot."
 *
 * WHY THIS IS NOT JUST A FollowPathCommand: a Pedro path needs a start point, and a pre-built path
 * carries a start point baked in from when it was built. That is fine for an auto whose whole route
 * is planned up front, and wrong for "go there from wherever you happen to be" — after a knock, a
 * missed grab, or a driver handoff, the robot is not where the plan assumed. So this command builds
 * its path in {@link #initialize()}, at the moment it actually runs, from the live pose. That is the
 * one idea the whole class exists for.
 *
 * USE IT IN A COMMAND TREE:
 *   schedule(new SequentialCommandGroup(
 *       new DriveToPoseCommand(follower, SCORING_POSE),
 *       mechanism.scoreCommand()
 *   ));
 *
 * TARGET CAN BE DEFERRED: the {@link Supplier} constructor re-reads the target each time the command
 * starts, so a target that is not known when the tree is built — a vision result, an alliance-
 * dependent spot — still works. A plain Pose is captured as a fixed target.
 *
 * REQUIREMENTS: this command does not claim a subsystem on its own, because it drives through the
 * Pedro follower rather than a subsystem. If you also have commands that drive the Drivetrain
 * directly, chain {@code .addRequirements(drivetrain)} so the scheduler can stop the two from
 * fighting over the same motors (CLAUDE.md §2).
 *
 * TIMEOUT IS BUILT IN, not optional (CLAUDE.md §5: nothing may hang the robot through a whole
 * match). It defaults to {@link Drivetrain#driveToPoseTimeoutSec}, which is live-tunable. A timeout
 * stops the robot and logs loudly — reaching it means the robot did not get there, and that should
 * never pass silently.
 */
public class DriveToPoseCommand extends CommandBase {

    /**
     * Below this distance we do not build a path at all. Pedro computes {@code 1 / length} when it
     * initializes a line, so a zero-length path yields infinity and a NaN tangent — the robot would
     * be handed a garbage path. Half an inch is well inside any useful positioning tolerance.
     */
    private static final double MIN_PATH_INCHES = 0.5;

    private final Follower follower;
    private final Supplier<Pose> target;
    private final ElapsedTime timer = new ElapsedTime();

    private double maxPower = 1.0;
    private boolean holdEnd = true;
    private double timeoutSeconds = -1; // < 0 means "use the live Drivetrain default"

    /** Set at initialize() so isFinished()/end() do not re-read a supplier that could change. */
    private Pose resolvedTarget;
    private boolean alreadyThere;
    private boolean timedOut;

    public DriveToPoseCommand(Follower follower, Pose target) {
        this(follower, () -> target);
    }

    public DriveToPoseCommand(Follower follower, Supplier<Pose> target) {
        this.follower = follower;
        this.target = target;
    }

    /** @param maxPower 0..1 cap on drive power for this move. Lower it for a delicate approach. */
    public DriveToPoseCommand setMaxPower(double maxPower) {
        this.maxPower = maxPower;
        return this;
    }

    /** @param holdEnd true to keep fighting to stay on the spot after arriving (the default). */
    public DriveToPoseCommand setHoldEnd(boolean holdEnd) {
        this.holdEnd = holdEnd;
        return this;
    }

    /** Overrides the live default from {@link Drivetrain#driveToPoseTimeoutSec} for this move. */
    public DriveToPoseCommand setTimeout(double seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    // ---- Pure logic (unit-tested off the robot, CLAUDE.md §9) ---------------------------------

    /** Straight-line distance between two poses, in inches. Heading is ignored. */
    public static double distanceInches(Pose from, Pose to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return Math.hypot(dx, dy);
    }

    /** True when the robot is close enough that building a path would be degenerate. */
    public static boolean isAlreadyThere(Pose from, Pose to, double minInches) {
        return distanceInches(from, to) < minInches;
    }

    // ---- Command lifecycle ---------------------------------------------------------------------

    @Override
    public void initialize() {
        timer.reset();
        timedOut = false;
        resolvedTarget = target.get();

        Pose start = follower.getPose();
        alreadyThere = isAlreadyThere(start, resolvedTarget, MIN_PATH_INCHES);

        if (alreadyThere) {
            // No path to build. Still square up on the target heading if we were asked to hold,
            // because "already in position" should not mean "pointing the wrong way".
            if (holdEnd) {
                follower.holdPoint(resolvedTarget);
            }
            return;
        }

        follower.setMaxPower(maxPower);

        PathChain path = follower.pathBuilder()
                .addPath(new BezierLine(start, resolvedTarget))
                .setLinearHeadingInterpolation(start.getHeading(), resolvedTarget.getHeading())
                .build();

        follower.followPath(path, holdEnd);
    }

    @Override
    public boolean isFinished() {
        if (alreadyThere) return true;

        if (timer.seconds() >= effectiveTimeout()) {
            timedOut = true;
            RobotLog.ww("DriveToPose", "TIMEOUT after %.1fs — wanted (%.1f, %.1f) but stopped at "
                            + "(%.1f, %.1f), %.1f in short. Robot stopped.",
                    timer.seconds(), resolvedTarget.getX(), resolvedTarget.getY(),
                    follower.getPose().getX(), follower.getPose().getY(),
                    distanceInches(follower.getPose(), resolvedTarget));
            return true;
        }

        return !follower.isBusy();
    }

    @Override
    public void end(boolean interrupted) {
        // Give up driving toward a pose we are no longer chasing. On a clean arrival with holdEnd
        // set, Pedro is already holding the spot and must be left alone to keep doing it.
        if (interrupted || timedOut) {
            follower.breakFollowing();
        }
    }

    private double effectiveTimeout() {
        return timeoutSeconds >= 0 ? timeoutSeconds : Drivetrain.driveToPoseTimeoutSec;
    }
}
