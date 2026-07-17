package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.JoystickCurve;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot tests for the joystick curve. Runs with ./gradlew :TeamCode:test.
 *
 * Uses typical settings: deadzone=0.05, minOutput=0.0, transitionPoint=0.5, transitionOutput=0.35.
 * These are the numbers a driver would start with; the shape is more important than exact values.
 */
public class JoystickCurveTest {

    private static final double DELTA = 0.001;
    private static final double DEADZONE = 0.05;
    private static final double MIN_OUT = 0.0;
    private static final double TRANSITION = 0.5;
    private static final double TRANSITION_OUT = 0.35;

    @Test
    public void inputInsideDeadzone_returnsZero() {
        assertEquals(0.0, JoystickCurve.apply(0.0, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT), DELTA);
        assertEquals(0.0, JoystickCurve.apply(0.04, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT), DELTA);
        assertEquals(0.0, JoystickCurve.apply(-0.04, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT), DELTA);
    }

    @Test
    public void inputAtFullPositive_returnsOne() {
        assertEquals(1.0, JoystickCurve.apply(1.0, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT), DELTA);
    }

    @Test
    public void inputAtFullNegative_returnsMinusOne() {
        assertEquals(-1.0, JoystickCurve.apply(-1.0, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT), DELTA);
    }

    @Test
    public void signIsPreserved() {
        double pos = JoystickCurve.apply(0.5, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT);
        double neg = JoystickCurve.apply(-0.5, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT);
        assertEquals(pos, -neg, DELTA);
    }

    @Test
    public void lowSpeedRegion_isBelowLinear() {
        // At mid-range input, curve should give LESS than linear (finer low-speed control).
        double curved = JoystickCurve.apply(0.3, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT);
        assertTrue("Curve should be shallower than linear at low speed", curved < 0.3);
    }

    @Test
    public void curveIsMonotonic() {
        // Output must never decrease as input increases past the deadzone.
        double prev = 0;
        for (double in = 0.06; in <= 1.0; in += 0.02) {
            double out = JoystickCurve.apply(in, DEADZONE, MIN_OUT, TRANSITION, TRANSITION_OUT);
            assertTrue("Curve went backwards at input=" + in, out >= prev - DELTA);
            prev = out;
        }
    }
}