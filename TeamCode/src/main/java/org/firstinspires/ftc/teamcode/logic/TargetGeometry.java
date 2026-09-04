package org.firstinspires.ftc.teamcode.logic;

/**
 * TargetGeometry — turns a camera angle into a distance on the floor.
 *
 * Pure math, no hardware, so it is unit-tested off the robot (CLAUDE.md §9). The Vision subsystem
 * owns the camera and calls these functions; nothing in this file knows what a Limelight is.
 *
 * THE PICTURE. The camera sits at a known height and is tilted down by a known angle. The target
 * sits on the floor, so its center is one radius up. The camera reports ty, the angle of the target
 * above the middle of its own view. Subtract ty from the camera's downward tilt and you have the
 * angle below horizontal to the target. That angle and the height difference make a right triangle,
 * and the distance along the floor is the side we want.
 *
 * SIGN CONVENTIONS — this is where the math usually goes wrong, so they are stated once here and
 * used everywhere:
 *   - cameraPitchDegrees is how far the camera is tilted DOWN from horizontal. Always positive.
 *   - ty is positive when the target sits ABOVE the middle of the view. A closer target sits lower
 *     in the frame, so it has a smaller (or negative) ty and a larger angle below horizontal.
 *   - tx is positive when the target is to the RIGHT of the middle. lateralOffsetInches flips that,
 *     so the value it returns is positive to the LEFT — the same direction as the robot's +y axis,
 *     which is what a caller steering the robot needs.
 *
 * VERIFY THE SIGNS ON THE ROBOT. Put the target clearly to one side and read the number. A sign
 * error here drives the robot away from the target as confidently as a correct one drives it home.
 */
public final class TargetGeometry {

    private TargetGeometry() {}

    /** Angle from the lens down to the target, in degrees. Larger means closer to the robot. */
    public static double angleBelowHorizonDegrees(double cameraPitchDegrees, double tyDegrees) {
        return cameraPitchDegrees - tyDegrees;
    }

    /**
     * True when the target is far enough below horizontal to solve for a distance.
     *
     * As the angle goes to zero the sight line becomes parallel with the floor and the distance
     * runs away to infinity, so a tiny angle error becomes a huge distance error. Below the minimum
     * we report nothing rather than a number nobody should trust.
     */
    public static boolean canSolve(double cameraHeightInches, double targetCenterHeightInches,
                                   double cameraPitchDegrees, double tyDegrees,
                                   double minAngleDegrees) {
        if (!(cameraHeightInches > targetCenterHeightInches)) return false;
        return angleBelowHorizonDegrees(cameraPitchDegrees, tyDegrees) > minAngleDegrees;
    }

    /**
     * Distance along the floor, from the point under the lens to the point under the target center.
     *
     * Returns NaN when it cannot be solved — the target is at or above the horizon, or the camera is
     * mounted at or below the height of the target center. NaN rather than a wrong number on
     * purpose: a caller must decide what to do with "I do not know", and a plausible-looking wrong
     * distance is the more dangerous of the two.
     *
     * @param cameraHeightInches      height of the lens above the floor
     * @param targetCenterHeightInches height of the target's center above the floor (a ball's radius)
     * @param cameraPitchDegrees      how far the camera is tilted down from horizontal, positive
     * @param tyDegrees               ty from the camera, positive up
     * @param minAngleDegrees         smallest angle below horizontal that is still trustworthy
     */
    public static double groundRangeInches(double cameraHeightInches, double targetCenterHeightInches,
                                           double cameraPitchDegrees, double tyDegrees,
                                           double minAngleDegrees) {
        if (!canSolve(cameraHeightInches, targetCenterHeightInches,
                cameraPitchDegrees, tyDegrees, minAngleDegrees)) {
            return Double.NaN;
        }
        double heightDelta = cameraHeightInches - targetCenterHeightInches;
        double angle = Math.toRadians(angleBelowHorizonDegrees(cameraPitchDegrees, tyDegrees));
        return heightDelta / Math.tan(angle);
    }

    /**
     * How far the target sits to the side, in inches. Positive is LEFT (see the sign note above).
     *
     * This assumes the target is near the middle of the view left-to-right, which is true while you
     * are aiming at it. Far out to one side the range itself is measured along the sight line rather
     * than straight ahead, and this reads slightly long. Returns NaN if the range is NaN, so a
     * missing distance stays missing instead of turning into a confident zero.
     */
    public static double lateralOffsetInches(double groundRangeInches, double txDegrees) {
        return groundRangeInches * Math.tan(Math.toRadians(-txDegrees));
    }
}
