package org.firstinspires.ftc.teamcode.util;

import com.pedropathing.geometry.Pose;
import com.pedropathing.math.MathFunctions;

/**
 * AllianceMirror — write every auto once, for BLUE, and let the code produce the RED version.
 *
 * THE CONVENTION, and it only works if everyone follows it: **author every field pose for BLUE.**
 * Nothing anywhere should contain a hand-written red pose. Red is derived, always, by mirroring the
 * blue one. The moment someone hand-writes a red pose, the two alliances drift apart and you are
 * maintaining two autos again — which is the exact problem this class exists to remove.
 *
 * WHICH MIRROR: FTC fields are symmetric, but not all in the same way, and the right transform is a
 * property of the season's game.
 *
 *   ROTATIONAL — spin the field 180° about its centre. Red and blue are diagonally opposite.
 *   MIRROR_X   — reflect across the vertical centre line. Red and blue face each other left/right.
 *   MIRROR_Y   — reflect across the horizontal centre line. Red and blue face each other near/far.
 *
 * **KICKOFF TASK: set {@link #seasonSymmetry} to match the real field, then check it on a real
 * field before trusting an auto to it.** Getting this wrong does not fail loudly — it drives a
 * confident, well-tuned path into the wrong quarter of the field.
 *
 * WHY THIS IS A CODE CONSTANT AND NOT A DASHBOARD KNOB: it is decided once at kickoff and never
 * turned again, so it belongs in git like the Pedro constants do (CLAUDE.md §6). It is also the kind
 * of value that must never differ between what is committed and what is running.
 *
 * MIRRORING PATHS: mirror the POSES, then build the path from them. Do not try to mirror a built
 * path — its control points are already baked.
 *
 * Every method here is pure, so the transforms are unit-tested off the robot (CLAUDE.md §9).
 */
public final class AllianceMirror {

    /** A competition FTC field is 144 inches square, so its centre is (72, 72) in Pedro coords. */
    public static final double FIELD_INCHES = 144.0;

    public enum Symmetry { ROTATIONAL, MIRROR_X, MIRROR_Y }

    /** KICKOFF: set to the season's field symmetry, then verify it on a real field. */
    public static Symmetry seasonSymmetry = Symmetry.ROTATIONAL;

    private AllianceMirror() { } // static holder; never instantiated

    /**
     * Mirrors a pose to the other side of the field.
     *
     * Each transform is its own inverse — mirroring twice returns the original pose — which is what
     * makes "author blue, derive red" safe to apply anywhere without tracking how many times it ran.
     */
    public static Pose mirror(Pose pose, Symmetry symmetry) {
        switch (symmetry) {
            case MIRROR_X:
                // Reflect across the vertical centre line: x flips, y stays, heading reflects about
                // the y-axis. Facing +x becomes facing -x; facing +y is unchanged.
                return new Pose(FIELD_INCHES - pose.getX(), pose.getY(),
                        MathFunctions.normalizeAngle(Math.PI - pose.getHeading()));

            case MIRROR_Y:
                // Reflect across the horizontal centre line: y flips, x stays, heading reflects
                // about the x-axis. Facing +y becomes facing -y; facing +x is unchanged.
                return new Pose(pose.getX(), FIELD_INCHES - pose.getY(),
                        MathFunctions.normalizeAngle(-pose.getHeading()));

            case ROTATIONAL:
            default:
                // Rotate 180° about the field centre: both axes flip and the robot faces the
                // opposite way.
                return new Pose(FIELD_INCHES - pose.getX(), FIELD_INCHES - pose.getY(),
                        MathFunctions.normalizeAngle(pose.getHeading() + Math.PI));
        }
    }

    /** Mirrors using the season's configured symmetry. */
    public static Pose mirror(Pose pose) {
        return mirror(pose, seasonSymmetry);
    }

    /**
     * Turns a blue-authored pose into the pose for the alliance we are actually playing.
     *
     * @param authoredForBlue the pose as written for the BLUE alliance
     * @param isRed           true when playing RED, in which case the pose is mirrored
     */
    public static Pose forAlliance(Pose authoredForBlue, boolean isRed, Symmetry symmetry) {
        return isRed ? mirror(authoredForBlue, symmetry) : authoredForBlue;
    }

    /** As above, using the season's configured symmetry. */
    public static Pose forAlliance(Pose authoredForBlue, boolean isRed) {
        return forAlliance(authoredForBlue, isRed, seasonSymmetry);
    }
}
