package org.firstinspires.ftc.teamcode.util;

import com.qualcomm.robotcore.util.ElapsedTime;

/**
 * SlewRateLimiter — caps how fast a value can change per second.
 *
 * WHY: For lifts, arms, or intakes, a sudden power spike causes jerk that can shake the robot, hurt
 * mechanical linkages, or unseat game pieces. This limits acceleration to a set rate. Use different
 * positive/negative rates when the mechanism has gravity assist (e.g. lift going down can decelerate
 * harder than it accelerates going up).
 *
 * WHY NOT USE IT: Drivetrain acceleration is best handled by Pedro's tuner and the drive team's
 * feel. Reach for slew limiting when a mechanism is genuinely jerky, not preemptively.
 *
 * Ported from FTC 5327 SMS Robotics' decode-2025 (common/drive/SlewRateLimiter.java); androidx
 * MathUtils.clamp inlined so we don't drag in an extra dep for one clamp call.
 */
public class SlewRateLimiter {
    private final double positiveRateLimit;
    private final double negativeRateLimit;
    private final ElapsedTime timer;
    private double prevVal;
    private double prevTime;

    /** Asymmetric limits — use when up and down should differ (gravity, safety). */
    public SlewRateLimiter(double positiveRateLimit, double negativeRateLimit, double initialValue) {
        this.positiveRateLimit = positiveRateLimit;
        this.negativeRateLimit = negativeRateLimit;
        this.prevVal = initialValue;
        this.prevTime = 0;
        this.timer = new ElapsedTime();
    }

    /** Symmetric limit (|positive| = |negative| = rateLimit). */
    public SlewRateLimiter(double rateLimit, double initialValue) {
        this(rateLimit, -rateLimit, initialValue);
    }

    /** Symmetric limit starting from 0. */
    public SlewRateLimiter(double rateLimit) {
        this(rateLimit, -rateLimit, 0);
    }

    /**
     * Steps the limited output toward input by at most (positiveRate * dt) or (negativeRate * dt).
     * Call once per loop with the current desired input; use the returned value as the command.
     */
    public double calculate(double input) {
        double currentTime = timer.seconds();
        double elapsedTime = currentTime - prevTime;

        double delta = input - prevVal;
        double maxUp = positiveRateLimit * elapsedTime;
        double maxDown = negativeRateLimit * elapsedTime;
        // inline clamp: no androidx MathUtils, one less dep on the loop
        double clampedDelta = Math.max(maxDown, Math.min(maxUp, delta));

        prevVal += clampedDelta;
        prevTime = currentTime;
        return prevVal;
    }
}