package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bylazar.configurables.annotations.IgnoreConfigurable;

import org.firstinspires.ftc.teamcode.util.Persistence;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Which fields get written into a robot's tuning file (CLAUDE.md §9).
 *
 * WHY THIS EXISTS: the rule used to be "any public static field", which is not the rule Panels uses
 * to decide what to show. Vision declares two Problem descriptors as public static final constants —
 * not tunables by any reading — and they were saved into comp_tuning.json as nested objects the load
 * could never restore, so the robot reported values missing at every init. These tests pin the rule
 * to Panels': non-final, static, not ignored.
 */
public class PersistenceTunableFieldTest {

    /** Stand-in for a tunable class, with one field of each shape we care about. */
    @SuppressWarnings("unused")
    static class Fixture {
        public static double speedCap = 1.0;                    // a real tunable
        public static boolean enabled = true;                   // a real tunable
        public static final double NOT_A_KNOB = 3.0;            // constant, not a tunable
        @IgnoreConfigurable public static double hidden = 5.0;  // deliberately excluded
        private static double notPublic = 7.0;                  // not visible to the dashboard
        public double instanceField = 9.0;                      // not static
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Fixture.class.getDeclaredField(name);
    }

    @Test
    public void savesPublicStaticNonFinalFields() throws Exception {
        assertTrue(Persistence.isTunable(field("speedCap")));
        assertTrue(Persistence.isTunable(field("enabled")));
    }

    /** The regression: a constant is not a tunable, and saving one produces a value that cannot load. */
    @Test
    public void skipsFinalConstants() throws Exception {
        assertFalse(Persistence.isTunable(field("NOT_A_KNOB")));
    }

    @Test
    public void skipsFieldsHiddenFromTheDashboard() throws Exception {
        assertFalse(Persistence.isTunable(field("hidden")));
    }

    @Test
    public void skipsNonPublicAndNonStaticFields() throws Exception {
        assertFalse(Persistence.isTunable(field("notPublic")));
        assertFalse(Persistence.isTunable(field("instanceField")));
    }

    /** The specific fields that caused this: Vision's Problem constants must never be saved. */
    @Test
    public void skipsTheVisionProblemConstants() throws Exception {
        Class<?> vision = Class.forName("org.firstinspires.ftc.teamcode.subsystems.Vision");
        assertFalse(Persistence.isTunable(vision.getDeclaredField("CAMERA_DOWN")));
        assertFalse(Persistence.isTunable(vision.getDeclaredField("TARGET_STALE")));
        // ...while the camera calibration next to them still is.
        assertTrue(Persistence.isTunable(vision.getDeclaredField("cameraPitchDegrees")));
    }
}
