package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Off-robot unit tests for IntakeLogic (CLAUDE.md section 9).
 * Run on your laptop with `./gradlew :TeamCode:test` — no robot required.
 * Add a case here every time you (or the AI) change the logic.
 */
public class IntakeLogicTest {

    @Test
    public void stopped_isZero() {
        assertEquals(0.0, IntakeLogic.powerFor(IntakeLogic.Mode.STOPPED, 0.9, 0.7), 1e-9);
    }

    @Test
    public void intaking_isPositiveIntakePower() {
        assertEquals(0.9, IntakeLogic.powerFor(IntakeLogic.Mode.INTAKING, 0.9, 0.7), 1e-9);
    }

    @Test
    public void ejecting_isNegativeEjectPower() {
        assertEquals(-0.7, IntakeLogic.powerFor(IntakeLogic.Mode.EJECTING, 0.9, 0.7), 1e-9);
    }

    @Test
    public void powersAreClampedToSafeRange() {
        assertEquals(1.0, IntakeLogic.powerFor(IntakeLogic.Mode.INTAKING, 5.0, 0.0), 1e-9);
    }
}
