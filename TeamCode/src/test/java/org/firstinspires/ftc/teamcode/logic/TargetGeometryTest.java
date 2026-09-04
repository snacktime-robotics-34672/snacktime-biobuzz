package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the camera distance math, run off the robot (CLAUDE.md §9).
 *
 * The numbers here come from the geometry itself, not from a run: with the lens 8 inches up, the
 * ball center 1.875 up and the sight line 20 degrees below horizontal, the distance along the floor
 * is (8 - 1.875) / tan(20 deg) = 16.83 inches. Everything else is a variation on that one triangle.
 */
public class TargetGeometryTest {

    private static final double CAMERA_HEIGHT = 8.0;
    private static final double BALL_CENTER = 1.875;
    private static final double PITCH = 20.0;
    private static final double MIN_ANGLE = 1.0;

    private static double range(double pitch, double ty) {
        return TargetGeometry.groundRangeInches(CAMERA_HEIGHT, BALL_CENTER, pitch, ty, MIN_ANGLE);
    }

    @Test
    public void computesTheDistanceForATargetOnTheOpticalAxis() {
        assertEquals(16.830, range(PITCH, 0.0), 0.01);
    }

    /** ty is positive UP, so a positive ty raises the sight line and the target is further away. */
    @Test
    public void aHigherTargetInTheFrameIsFurtherAway() {
        assertTrue(range(PITCH, 5.0) > range(PITCH, 0.0));
        assertTrue(range(PITCH, -5.0) < range(PITCH, 0.0));
    }

    /** The error the calibration procedure teaches you to read: too small a pitch reads too far. */
    @Test
    public void tooSmallAPitchReportsTooLargeADistance() {
        assertTrue(range(PITCH - 5.0, 0.0) > range(PITCH, 0.0));
        assertTrue(range(PITCH + 5.0, 0.0) < range(PITCH, 0.0));
    }

    @Test
    public void refusesToSolveAtOrAboveTheHorizon() {
        // Sight line exactly level: the distance is infinite, so there is no answer to give.
        assertTrue(Double.isNaN(range(PITCH, PITCH)));
        // Above the horizon: the ball cannot be on the floor and up there at the same time.
        assertTrue(Double.isNaN(range(PITCH, PITCH + 5.0)));
        // Inside the minimum angle, where a tiny angle error becomes a huge distance error.
        assertTrue(Double.isNaN(range(PITCH, PITCH - 0.5)));
        // Just outside it, an answer comes back.
        assertFalse(Double.isNaN(range(PITCH, PITCH - 1.5)));
    }

    /** A lens at or below the ball center cannot see it below the horizon at all. */
    @Test
    public void refusesToSolveWhenTheCameraIsNotAboveTheTarget() {
        assertTrue(Double.isNaN(TargetGeometry.groundRangeInches(
                1.0, BALL_CENTER, PITCH, 0.0, MIN_ANGLE)));
        assertFalse(TargetGeometry.canSolve(BALL_CENTER, BALL_CENTER, PITCH, 0.0, MIN_ANGLE));
    }

    @Test
    public void angleBelowHorizonSubtractsTy() {
        assertEquals(15.0, TargetGeometry.angleBelowHorizonDegrees(20.0, 5.0), 1e-9);
        assertEquals(25.0, TargetGeometry.angleBelowHorizonDegrees(20.0, -5.0), 1e-9);
    }

    /** tx is positive RIGHT, and the offset we report is positive LEFT — so the sign flips. */
    @Test
    public void lateralOffsetIsPositiveToTheLeft() {
        assertTrue(TargetGeometry.lateralOffsetInches(40.0, -10.0) > 0.0);
        assertTrue(TargetGeometry.lateralOffsetInches(40.0, 10.0) < 0.0);
        assertEquals(0.0, TargetGeometry.lateralOffsetInches(40.0, 0.0), 1e-9);
        assertEquals(7.053, TargetGeometry.lateralOffsetInches(40.0, -10.0), 0.01);
    }

    /** No distance means no sideways offset either — it must not become a confident zero. */
    @Test
    public void lateralOffsetStaysUnknownWhenTheRangeIsUnknown() {
        assertTrue(Double.isNaN(TargetGeometry.lateralOffsetInches(Double.NaN, 5.0)));
    }
}
