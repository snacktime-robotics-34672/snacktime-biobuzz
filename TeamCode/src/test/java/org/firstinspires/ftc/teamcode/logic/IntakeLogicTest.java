package org.firstinspires.ftc.teamcode.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Off-robot tests for the intake trigger math (§9).
 *
 * The cases that matter are the edges: a trigger resting just off zero must NOT start the intake,
 * and a bad power in the dashboard must NOT reach the motor.
 */
public class IntakeLogicTest {

    private static final double EPS = 1e-9;

    @Test
    public void triggerAtRestLeavesIntakeOff() {
        // A real trigger rests near, but not exactly at, zero.
        assertEquals(0.0, IntakeLogic.powerForTrigger(0.0, 0.25, 0.8, 1.0), EPS);
        assertEquals(0.0, IntakeLogic.powerForTrigger(0.04, 0.25, 0.8, 1.0), EPS);
    }

    @Test
    public void justBelowThresholdIsOffAndAtThresholdIsOn() {
        assertEquals(0.0, IntakeLogic.powerForTrigger(0.2499, 0.25, 0.8, 1.0), EPS);
        assertEquals(0.8, IntakeLogic.powerForTrigger(0.25, 0.25, 0.8, 1.0), EPS);
    }

    @Test
    public void fullSqueezeRunsAtTunedPowerNotAtFullPower() {
        // Hold-to-run, not a speed dial: squeezing harder does not run the intake faster.
        assertEquals(0.8, IntakeLogic.powerForTrigger(1.0, 0.25, 0.8, 1.0), EPS);
        assertEquals(0.8, IntakeLogic.powerForTrigger(0.6, 0.25, 0.8, 1.0), EPS);
    }

    @Test
    public void safetyCapLimitsBothDirections() {
        assertEquals(0.5, IntakeLogic.powerForTrigger(1.0, 0.25, 0.9, 0.5), EPS);
        assertEquals(-0.5, IntakeLogic.powerForTrigger(1.0, 0.25, -0.9, 0.5), EPS);
    }

    @Test
    public void zeroCapStopsTheMotorEntirely() {
        assertEquals(0.0, IntakeLogic.powerForTrigger(1.0, 0.25, 0.8, 0.0), EPS);
        // A negative cap is nonsense; treat it as "no power" rather than inverting the limit.
        assertEquals(0.0, IntakeLogic.powerForTrigger(1.0, 0.25, 0.8, -0.5), EPS);
    }

    @Test
    public void nanNeverReachesTheMotor() {
        assertEquals(0.0, IntakeLogic.powerForTrigger(Double.NaN, 0.25, 0.8, 1.0), EPS);
        assertEquals(0.0, IntakeLogic.powerForTrigger(1.0, 0.25, Double.NaN, 1.0), EPS);
    }

    @Test
    public void clampPassesValuesInsideTheLimitThrough() {
        assertEquals(0.3, IntakeLogic.clamp(0.3, 1.0), EPS);
        assertEquals(-0.3, IntakeLogic.clamp(-0.3, 1.0), EPS);
    }
}
