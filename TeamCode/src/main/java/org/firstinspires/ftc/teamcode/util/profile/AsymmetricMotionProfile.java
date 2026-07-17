package org.firstinspires.ftc.teamcode.util.profile;

/**
 * AsymmetricMotionProfile — asymmetric trapezoidal motion profile for a 1-DOF actuator.
 *
 * WHY THIS EXISTS (not in SolversLib): SolversLib ships `TrapezoidProfile` and
 * `TrapezoidProfileCommand`, but they're symmetric — same accel and decel. For mechanisms with
 * gravity assist (a lift going down, an arm settling) you often want to accelerate gently but
 * decelerate hard. That's what this class adds.
 *
 * HOW IT WORKS: Given start position, end position, and (velo, accel, decel), it precomputes the
 * three phases:
 *   t1  — accelerate from 0 to (a bounded) cruise velocity
 *   t2  — cruise (may be 0 if the move is short enough that cruise velocity is never reached)
 *   t3  — decelerate to rest at the target
 * If the distance is too short for full cruise (a triangular profile), it solves for the peak
 * velocity that fits. Reverse moves are handled by mirroring internally.
 *
 * USAGE:
 *   ProfileConstraints c = new ProfileConstraints(velo, accel, decel);
 *   AsymmetricMotionProfile profile = new AsymmetricMotionProfile(start, target, c);
 *   ProfileState s = profile.calculate(elapsedTime); // s.x is the setpoint at this instant
 *
 * WHERE TO USE IT: only where you're actually tracking a position setpoint over time — an arm or
 * lift going to a specific position. Not appropriate for velocity-controlled mechanisms or the
 * drivetrain (Pedro handles that).
 *
 * Ported from FTC 5327 SMS Robotics' decode-2025 (common/drive/pathing/geometry/profile/); the
 * math is verbatim, we just moved it into our util/profile package and removed the androidx
 * @NonNull annotation on toString().
 */
public class AsymmetricMotionProfile {
    public double initialPosition;
    public double finalPosition;
    public double distance;
    public double t1, t2, t3;
    public double totalTime;
    public double t1_stop_position;
    public double max_velocity;
    public double t2_stop_position;
    public boolean flipped = false;
    public double originalPos = 0;

    public ProfileState state = new ProfileState();
    public ProfileConstraints constraints;

    public AsymmetricMotionProfile(double initialPosition, double finalPosition, ProfileConstraints constraints) {
        // If the target is behind us, mirror the problem: compute a forward profile and flip at output.
        if (finalPosition < initialPosition) {
            flipped = true;
            this.originalPos = initialPosition;
            double temp = initialPosition;
            initialPosition = finalPosition;
            finalPosition = temp;
        }
        this.initialPosition = initialPosition;
        this.finalPosition = finalPosition;
        this.distance = finalPosition - initialPosition;
        this.constraints = constraints;

        // Naive trapezoid: time to accelerate + time at cruise + time to decelerate.
        t1 = constraints.velo / constraints.accel;
        t3 = constraints.velo / constraints.decel;
        t2 = Math.abs(distance) / constraints.velo - (t1 + t3) / 2;

        if (t2 < 0) {
            // Not enough distance to reach cruise velocity — solve for a triangular profile whose
            // peak velocity is smaller than constraints.velo but still uses the full distance.
            this.t2 = 0;

            double a = (constraints.accel / 2) * (1 - constraints.accel / -constraints.decel);
            double c = -distance;

            t1 = Math.sqrt(-4 * a * c) / (2 * a);
            t3 = -(constraints.accel * t1) / -constraints.decel;
            t1_stop_position = (constraints.accel * Math.pow(t1, 2)) / 2;

            max_velocity = constraints.accel * t1;

            t2_stop_position = t1_stop_position;
        } else {
            max_velocity = constraints.velo;
            t1_stop_position = (constraints.velo * t1) / 2;
            t2_stop_position = t1_stop_position + t2 * max_velocity;
        }

        totalTime = t1 + t2 + t3;
    }

    /** Returns the profile sample at the given elapsed time. Clamps past totalTime to the final pose. */
    public ProfileState calculate(final double time) {
        double position, velocity, acceleration, stage_time;
        if (time <= t1) {
            stage_time = time;
            acceleration = constraints.accel;
            velocity = acceleration * stage_time;
            position = velocity * stage_time / 2;
        } else if (time <= t1 + t2) {
            stage_time = time - t1;
            acceleration = 0;
            velocity = constraints.velo;
            position = t1_stop_position + stage_time * velocity;
        } else if (time <= totalTime) {
            stage_time = time - t1 - t2;
            acceleration = -constraints.decel;
            velocity = max_velocity - stage_time * constraints.decel;
            position = t2_stop_position + (max_velocity + velocity) / 2 * stage_time;
        } else {
            acceleration = 0;
            velocity = 0;
            position = finalPosition;
        }

        if (time <= totalTime) {
            state.x = flipped ? (originalPos - position) : (initialPosition + position);
        } else {
            state.x = flipped ? initialPosition : (originalPos + position);
        }
        state.v = velocity;
        state.a = acceleration;
        return this.state;
    }

    @Override
    public String toString() {
        return String.format("%f, %f, %f", state.x, state.v, state.a);
    }
}