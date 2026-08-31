package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.firstinspires.ftc.teamcode.util.StandYourGround;
import org.firstinspires.ftc.teamcode.util.StandYourGround.State;
import org.junit.Test;

/**
 * Off-robot tests for the stand-your-ground decision logic (CLAUDE.md §9).
 *
 * These cover the transitions that decide whether the brace works at all. The hardware half —
 * capturing the pose and calling Pedro's holdPoint — is verified on the bench, not here.
 */
public class StandYourGroundTest {

    private static final double DELAY = 250;

    // ---- inputsAreIdle ------------------------------------------------------------------------

    @Test
    public void allZeroInputsAreIdle() {
        assertTrue(StandYourGround.inputsAreIdle(0, 0, 0));
    }

    @Test
    public void anyNonZeroInputIsNotIdle() {
        assertFalse(StandYourGround.inputsAreIdle(0.4, 0, 0));
        assertFalse(StandYourGround.inputsAreIdle(0, -0.4, 0));
        assertFalse(StandYourGround.inputsAreIdle(0, 0, 0.05));
    }

    // ---- Engaging -----------------------------------------------------------------------------

    @Test
    public void doesNotEngageBeforeTheSettleDelay() {
        assertEquals(State.DRIVING,
                StandYourGround.nextState(State.DRIVING, true, true, 100, DELAY));
    }

    @Test
    public void engagesOnceIdleLongerThanTheSettleDelay() {
        assertEquals(State.HOLDING,
                StandYourGround.nextState(State.DRIVING, true, true, 251, DELAY));
    }

    @Test
    public void engagesExactlyAtTheDelayBoundary() {
        assertEquals(State.HOLDING,
                StandYourGround.nextState(State.DRIVING, true, true, DELAY, DELAY));
    }

    @Test
    public void zeroDelayEngagesImmediately() {
        assertEquals(State.HOLDING,
                StandYourGround.nextState(State.DRIVING, true, true, 0, 0));
    }

    // ---- Releasing ----------------------------------------------------------------------------

    @Test
    public void anyStickInputReleasesTheHold() {
        assertEquals(State.DRIVING,
                StandYourGround.nextState(State.HOLDING, true, false, 0, DELAY));
    }

    @Test
    public void holdPersistsWhileInputsStayIdle() {
        // The driver has let go and is being pushed. The brace must not time out or drop on its own.
        assertEquals(State.HOLDING,
                StandYourGround.nextState(State.HOLDING, true, true, 30_000, DELAY));
    }

    // ---- The enable flag ----------------------------------------------------------------------

    @Test
    public void disablingReleasesAnActiveHold() {
        // Turning the tunable off from the dashboard must hand the robot back, not strand it braced.
        assertEquals(State.DRIVING,
                StandYourGround.nextState(State.HOLDING, false, true, 30_000, DELAY));
    }

    @Test
    public void disabledNeverEngages() {
        assertEquals(State.DRIVING,
                StandYourGround.nextState(State.DRIVING, false, true, 30_000, DELAY));
    }
}
