package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pedropathing.geometry.Pose;

import org.firstinspires.ftc.teamcode.commands.DriveToPoseCommand;
import org.junit.Test;

/**
 * Off-robot tests for the DriveToPoseCommand geometry (CLAUDE.md §9).
 *
 * The important one is {@link #alreadyThereGuardsTheDegeneratePath()}: Pedro computes 1/length when
 * it initializes a line, so building a path to where the robot already stands yields infinity and a
 * NaN tangent. That guard is the difference between "does nothing" and "drives off on a garbage
 * path", so it is worth pinning down here rather than discovering on the field.
 */
public class DriveToPoseTest {

    private static final double EPS = 1e-9;
    private static final double MIN = 0.5;

    @Test
    public void distanceIsStraightLine() {
        assertEquals(5.0,
                DriveToPoseCommand.distanceInches(new Pose(0, 0), new Pose(3, 4)), EPS);
    }

    @Test
    public void distanceIgnoresHeading() {
        // Same spot, opposite facing: the robot does not need to drive anywhere, only turn.
        Pose a = new Pose(10, 10, 0);
        Pose b = new Pose(10, 10, Math.PI);
        assertEquals(0.0, DriveToPoseCommand.distanceInches(a, b), EPS);
    }

    @Test
    public void distanceIsSymmetric() {
        Pose a = new Pose(-7, 2);
        Pose b = new Pose(11, -5);
        assertEquals(DriveToPoseCommand.distanceInches(a, b),
                DriveToPoseCommand.distanceInches(b, a), EPS);
    }

    @Test
    public void alreadyThereGuardsTheDegeneratePath() {
        Pose here = new Pose(24, 24);
        assertTrue(DriveToPoseCommand.isAlreadyThere(here, new Pose(24, 24), MIN));
    }

    @Test
    public void justInsideTheGuardCountsAsThere() {
        assertTrue(DriveToPoseCommand.isAlreadyThere(
                new Pose(0, 0), new Pose(0.4, 0), MIN));
    }

    @Test
    public void justOutsideTheGuardIsARealMove() {
        assertFalse(DriveToPoseCommand.isAlreadyThere(
                new Pose(0, 0), new Pose(0.6, 0), MIN));
    }

    @Test
    public void exactlyAtTheThresholdIsARealMove() {
        // Strictly-less-than, so the boundary builds a path rather than silently skipping the move.
        assertFalse(DriveToPoseCommand.isAlreadyThere(
                new Pose(0, 0), new Pose(MIN, 0), MIN));
    }

    @Test
    public void aNormalFieldMoveIsNotAlreadyThere() {
        assertFalse(DriveToPoseCommand.isAlreadyThere(
                new Pose(0, 0), new Pose(48, 36), MIN));
    }
}
