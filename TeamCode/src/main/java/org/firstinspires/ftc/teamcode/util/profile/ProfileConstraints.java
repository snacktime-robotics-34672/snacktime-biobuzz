package org.firstinspires.ftc.teamcode.util.profile;

/**
 * ProfileConstraints — the three numbers that define an asymmetric trapezoidal motion profile:
 * cruise velocity, acceleration (going up in speed), and deceleration (coming down in speed).
 *
 * Asymmetric because mechanisms with gravity assist (a lift going down, an arm falling toward
 * rest) can safely decelerate harder than they accelerate. Symmetric = pass the same value for
 * accel and decel.
 *
 * All values are stored as magnitudes (positive); direction is handled by the profile itself.
 */
public class ProfileConstraints {
    public double accel;
    public double decel;
    public double velo;

    /** Symmetric: accel == decel. */
    public ProfileConstraints(double velo, double accel) {
        this(velo, accel, accel);
    }

    /** Asymmetric. */
    public ProfileConstraints(double velo, double accel, double decel) {
        this.velo = Math.abs(velo);
        this.accel = Math.abs(accel);
        this.decel = Math.abs(decel);
    }

    /** Scale all three limits by the same factor — useful for unit conversion (e.g. rpm → rad/s). */
    public void convert(double factor) {
        this.velo *= factor;
        this.accel *= factor;
        this.decel *= factor;
    }
}