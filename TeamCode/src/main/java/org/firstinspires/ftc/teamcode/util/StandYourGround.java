package org.firstinspires.ftc.teamcode.util;

import com.pedropathing.follower.Follower;
import com.pedropathing.geometry.Pose;

import org.firstinspires.ftc.teamcode.subsystems.Drivetrain;

/**
 * StandYourGround — the defensive brace. When the driver lets go of the sticks, the robot stops
 * coasting and actively fights to stay where it is, so an opponent trying to shove us off a scoring
 * spot gets pushed back.
 *
 * HOW IT WORKS: two states, DRIVING and HOLDING. Let the sticks sit at zero and, after a short
 * settle delay, we capture the robot's pose ONCE and hand it to Pedro's own point-hold. Touch a
 * stick past the deadzone and the hold releases immediately, back to normal driving. The next return
 * to zero captures a fresh target.
 *
 * WHY CAPTURE ONCE, NEVER RE-READ: this is the whole trick. If the target pose were re-read every
 * loop, a steady push would walk the target along with the robot and the brace would do nothing —
 * it would be "hold wherever I am now", which is not holding at all. So the pose is captured only on
 * the DRIVING -> HOLDING edge and then left alone.
 *
 * WHY PEDRO'S holdPoint, not our own controller: it reuses the follower PIDFs already tuned in the
 * Tuning OpMode. Hold quality rides on that same tuning, so there is no second controller to tune
 * and no second set of gains to keep straight.
 *
 * WHY THE SETTLE DELAY: the robot is still moving when the stick is released. Capturing at that
 * exact instant captures a pose the robot then overshoots, so it would drive backwards to get back
 * to it — a lurch every time the driver lets go. Waiting {@link Drivetrain#holdEntryDelayMs} lets it
 * coast to a stop and hold where it actually ended up. Set the delay to 0 if you would rather it
 * snap back to the exact release point.
 *
 * DO NOT RUN WITH HEADING CORRECTION: Pedro's point-hold governs heading itself, so it and
 * {@code Drivetrain.headingHoldEnabled} would fight over the same motors. The OpMode warns if
 * both are switched on.
 *
 * The decision logic here is pure and static so it can be unit-tested off the robot (CLAUDE.md §9);
 * only {@link #update} touches hardware.
 */
public class StandYourGround {

    /**
     * DRIVING — the driver has the wheels. HOLDING — we are braced on a captured pose.
     * YIELDED — something else is driving the follower along a path (a {@code DriveToPoseCommand},
     * say), so the brace stands aside and touches nothing until that finishes.
     */
    public enum State { DRIVING, HOLDING, YIELDED }

    private State state = State.DRIVING;

    /** When the sticks last went idle, in nanos. Meaningless unless {@link #idle} is true. */
    private long idleSinceNanos;
    private boolean idle;

    /** The pose we are defending. Captured once on entry, never re-read while holding. */
    private Pose heldPose;

    public State getState()   { return state; }
    public boolean isHolding() { return state == State.HOLDING; }

    /** The pose being defended, or null when not holding. Telemetry and tests only. */
    public Pose getHeldPose() { return heldPose; }

    // ---- Pure decision logic (no hardware, unit-tested) ---------------------------------------

    /**
     * True when every drive input is at rest. Inputs are expected to have already been through the
     * deadzone, so a stick resting slightly off-centre reads as an exact zero here.
     */
    public static boolean inputsAreIdle(double forward, double strafe, double turn) {
        return forward == 0.0 && strafe == 0.0 && turn == 0.0;
    }

    /**
     * The state we should be in next.
     *
     * @param current      state we are in now
     * @param enabled      the live {@code holdWhenIdleEnabled} tunable
     * @param inputsIdle   result of {@link #inputsAreIdle}
     * @param idleMillis   how long the inputs have been idle; ignored unless inputsIdle
     * @param entryDelayMs how long they must stay idle before the brace engages
     */
    public static State nextState(State current, boolean enabled, boolean inputsIdle,
                                  double idleMillis, double entryDelayMs) {
        // Switching the tunable off mid-hold must release, not strand the robot in a brace it can
        // no longer be told to leave.
        if (!enabled) return State.DRIVING;
        if (!inputsIdle) return State.DRIVING;
        if (current == State.HOLDING) return State.HOLDING;
        return idleMillis >= entryDelayMs ? State.HOLDING : State.DRIVING;
    }

    // ---- Per-loop update (touches the follower) -----------------------------------------------

    /**
     * Advances the brace one loop. Call once per loop with the post-deadzone stick values, BEFORE
     * {@code follower.update()}.
     *
     * @return true if the caller must NOT issue a manual drive command this loop, because something
     *         else is driving the wheels — either this brace, or a path command we yielded to.
     */
    public boolean update(Follower follower, double forward, double strafe, double turn) {
        // A path command owns the follower. Stand aside and touch nothing: calling holdPoint or
        // startTeleopDrive here would break the path out from under it. isBusy() is true only while
        // following a path — our own hold sets it false — so this cannot be tripped by the brace.
        if (follower.isBusy()) {
            heldPose = null;
            idle = false; // restart the settle delay once we get control back
            state = State.YIELDED;
            return true;
        }

        if (state == State.YIELDED) {
            // The path finished. Pedro turned manual drive off when it took over, so hand the wheels
            // back before anything reads a stick this loop.
            follower.startTeleopDrive();
            state = State.DRIVING;
        }

        boolean inputsIdle = inputsAreIdle(forward, strafe, turn);

        long now = System.nanoTime();
        if (inputsIdle && !idle) {
            idle = true;
            idleSinceNanos = now;
        } else if (!inputsIdle) {
            idle = false;
        }

        double idleMillis = idle ? (now - idleSinceNanos) / 1_000_000.0 : 0.0;
        State next = nextState(state, Drivetrain.holdWhenIdleEnabled, inputsIdle,
                idleMillis, Drivetrain.holdEntryDelayMs);

        if (next != state) {
            if (next == State.HOLDING) {
                // Copied on purpose. Storing the follower's own Pose reference would risk defending
                // an object that keeps tracking the robot, which is the one bug that would make this
                // whole class do nothing. One allocation on a state edge, never in the steady loop
                // (§4 rule 8).
                Pose p = follower.getPose();
                heldPose = new Pose(p.getX(), p.getY(), p.getHeading());
                follower.holdPoint(heldPose, Drivetrain.holdUseScaling);
            } else {
                heldPose = null;
                // Hands the wheels back to open-loop teleop. Note this calls follower.update()
                // internally, so the release loop reads the Pinpoint twice — once here, once in the
                // OpMode. It happens on a stick-release edge only, never in steady state, and there
                // is no Pedro API to re-enter teleop without it (§4 rule 5).
                follower.startTeleopDrive();
            }
            state = next;
        }

        return state == State.HOLDING;
    }

    /** Drops any hold and returns to DRIVING. Call on OpMode stop, or when handing off control. */
    public void reset() {
        state = State.DRIVING;
        heldPose = null;
        idle = false;
    }
}
