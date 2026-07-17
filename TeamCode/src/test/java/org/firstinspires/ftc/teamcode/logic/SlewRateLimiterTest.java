package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.SlewRateLimiter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot tests for SlewRateLimiter. The rate limiter uses ElapsedTime internally which advances
 * with wall-clock, so these tests focus on behavior that holds regardless of exact dt: the very
 * first calculate() call, and eventual convergence.
 */
public class SlewRateLimiterTest {

    private static final double DELTA = 0.01;

    @Test
    public void firstCall_elapsedTimeIsNearZero_soOutputStaysNearInitial() {
        // ElapsedTime starts at ~0 the moment the limiter is created; the first calculate() call
        // has essentially no time to move, so output should still be at the initial value.
        SlewRateLimiter limiter = new SlewRateLimiter(1.0, 0.5, 0.0);
        double first = limiter.calculate(1.0);
        assertTrue("First call should barely move from initial", Math.abs(first - 0.0) < 0.1);
    }

    @Test
    public void constructor_symmetricRate_setsInitialValue() {
        SlewRateLimiter limiter = new SlewRateLimiter(0.5, 0.7);
        // First calculate with the initial value as input should return ~ initial.
        assertEquals(0.7, limiter.calculate(0.7), DELTA);
    }

    @Test
    public void repeatedCallsWithSameInputAsInitial_stayAtInitial() {
        SlewRateLimiter limiter = new SlewRateLimiter(1.0, 0.3);
        for (int i = 0; i < 10; i++) {
            assertEquals(0.3, limiter.calculate(0.3), DELTA);
        }
    }
}