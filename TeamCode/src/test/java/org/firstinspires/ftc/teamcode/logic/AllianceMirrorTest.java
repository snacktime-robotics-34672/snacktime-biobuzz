package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;

import com.pedropathing.geometry.Pose;

import org.firstinspires.ftc.teamcode.util.AllianceMirror;
import org.firstinspires.ftc.teamcode.util.AllianceMirror.Symmetry;
import org.junit.Test;

/**
 * Off-robot tests for alliance mirroring (CLAUDE.md §9).
 *
 * These matter more than most. A wrong mirror does not throw or log — it drives a confident,
 * well-tuned path into the wrong quarter of the field, and you only find out on a real field with
 * a match running. Pinning the transforms here is the cheapest place to catch that.
 */
public class AllianceMirrorTest {

    private static final double EPS = 1e-9;
    private static final double F = AllianceMirror.FIELD_INCHES; // 144
    private static final double CENTRE = F / 2.0;                // 72

    private static void assertPose(Pose expected, Pose actual) {
        assertEquals("x", expected.getX(), actual.getX(), EPS);
        assertEquals("y", expected.getY(), actual.getY(), EPS);
        assertEquals("heading", expected.getHeading(), actual.getHeading(), EPS);
    }

    // ---- The centre is the fixed point --------------------------------------------------------

    @Test
    public void fieldCentreStaysPutUnderEveryMirror() {
        Pose centre = new Pose(CENTRE, CENTRE, 0);
        assertEquals(CENTRE, AllianceMirror.mirror(centre, Symmetry.ROTATIONAL).getX(), EPS);
        assertEquals(CENTRE, AllianceMirror.mirror(centre, Symmetry.ROTATIONAL).getY(), EPS);
        assertEquals(CENTRE, AllianceMirror.mirror(centre, Symmetry.MIRROR_X).getX(), EPS);
        assertEquals(CENTRE, AllianceMirror.mirror(centre, Symmetry.MIRROR_Y).getY(), EPS);
    }

    // ---- Each transform ------------------------------------------------------------------------

    @Test
    public void rotationalFlipsBothAxesAndTurnsAround() {
        Pose blue = new Pose(12, 36, 0);
        assertPose(new Pose(F - 12, F - 36, Math.PI),
                AllianceMirror.mirror(blue, Symmetry.ROTATIONAL));
    }

    @Test
    public void mirrorXFlipsXAndReflectsHeadingAboutTheYAxis() {
        // Facing +x (heading 0) must come out facing -x (heading PI).
        assertPose(new Pose(F - 12, 36, Math.PI),
                AllianceMirror.mirror(new Pose(12, 36, 0), Symmetry.MIRROR_X));
        // Facing +y is along the mirror line, so it is unchanged.
        assertPose(new Pose(F - 12, 36, Math.PI / 2),
                AllianceMirror.mirror(new Pose(12, 36, Math.PI / 2), Symmetry.MIRROR_X));
    }

    @Test
    public void mirrorYFlipsYAndReflectsHeadingAboutTheXAxis() {
        // Facing +x is along the mirror line, so it is unchanged.
        assertPose(new Pose(12, F - 36, 0),
                AllianceMirror.mirror(new Pose(12, 36, 0), Symmetry.MIRROR_Y));
        // Facing +y must come out facing -y. Headings normalise to [0, 2PI), so -PI/2 reads 3PI/2.
        assertPose(new Pose(12, F - 36, 3 * Math.PI / 2),
                AllianceMirror.mirror(new Pose(12, 36, Math.PI / 2), Symmetry.MIRROR_Y));
    }

    // ---- Mirroring twice is the identity -------------------------------------------------------

    @Test
    public void everyMirrorIsItsOwnInverse() {
        // This is what makes "author blue, derive red" safe: applying the transform twice cannot
        // land somewhere new, so a double-mirror bug shows up as a no-op rather than a wrong spot.
        Pose blue = new Pose(23.5, 101.25, Math.toRadians(37));
        for (Symmetry s : Symmetry.values()) {
            Pose there = AllianceMirror.mirror(blue, s);
            Pose back = AllianceMirror.mirror(there, s);
            assertEquals(s + " x", blue.getX(), back.getX(), EPS);
            assertEquals(s + " y", blue.getY(), back.getY(), EPS);
            assertEquals(s + " heading", blue.getHeading(), back.getHeading(), EPS);
        }
    }

    // ---- forAlliance --------------------------------------------------------------------------

    @Test
    public void blueGetsTheAuthoredPoseUntouched() {
        Pose blue = new Pose(12, 36, Math.toRadians(90));
        assertPose(blue, AllianceMirror.forAlliance(blue, false, Symmetry.ROTATIONAL));
    }

    @Test
    public void redGetsTheMirroredPose() {
        Pose blue = new Pose(12, 36, 0);
        assertPose(AllianceMirror.mirror(blue, Symmetry.ROTATIONAL),
                AllianceMirror.forAlliance(blue, true, Symmetry.ROTATIONAL));
    }

    @Test
    public void headingsComeBackNormalised() {
        // A heading near a full turn must not come out as 2PI or negative — Pedro's own convention
        // is [0, 2PI), and a raw PI + PI would otherwise read as 2PI.
        Pose blue = new Pose(10, 10, Math.PI);
        double h = AllianceMirror.mirror(blue, Symmetry.ROTATIONAL).getHeading();
        assertEquals(0.0, h, EPS);
    }
}
