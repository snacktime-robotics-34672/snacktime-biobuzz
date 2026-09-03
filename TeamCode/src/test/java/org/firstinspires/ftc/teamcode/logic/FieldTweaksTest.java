package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.config.AutonFieldTweaks;
import org.firstinspires.ftc.teamcode.config.FieldTweaks;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Off-robot tests for the field-tweak lookup. Run with ./gradlew :TeamCode:test.
 *
 * WHY THIS EXISTS: flattening FieldTweaks traded six stored objects for eighteen statics and a
 * lookup built from eighteen hand-written ternaries. That is exactly the shape a transcription slip
 * hides in — swap one red for a blue and Field 2 quietly applies Field 1's correction, which on the
 * field looks like a tweak that gets worse the more you dial it in. Every combination is checked
 * against a value unique to it, so no swap can pass.
 */
public class FieldTweaksTest {

    /** Every combination gets its own recognisable number, so a mix-up cannot coincide. */
    private static void seedDistinctValues() {
        FieldTweaks.field1Red_xOffsetInches = 11;
        FieldTweaks.field1Red_yOffsetInches = 12;
        FieldTweaks.field1Red_headingOffsetDeg = 13;

        FieldTweaks.field1Blue_xOffsetInches = 21;
        FieldTweaks.field1Blue_yOffsetInches = 22;
        FieldTweaks.field1Blue_headingOffsetDeg = 23;

        FieldTweaks.field2Red_xOffsetInches = 31;
        FieldTweaks.field2Red_yOffsetInches = 32;
        FieldTweaks.field2Red_headingOffsetDeg = 33;

        FieldTweaks.field2Blue_xOffsetInches = 41;
        FieldTweaks.field2Blue_yOffsetInches = 42;
        FieldTweaks.field2Blue_headingOffsetDeg = 43;

        FieldTweaks.practiceRed_xOffsetInches = 51;
        FieldTweaks.practiceRed_yOffsetInches = 52;
        FieldTweaks.practiceRed_headingOffsetDeg = 53;

        FieldTweaks.practiceBlue_xOffsetInches = 61;
        FieldTweaks.practiceBlue_yOffsetInches = 62;
        FieldTweaks.practiceBlue_headingOffsetDeg = 63;
    }

    /** These are statics — leaving test values behind would leak into other tests. */
    @After
    public void clearValues() {
        FieldTweaks.field1Red_xOffsetInches = 0;
        FieldTweaks.field1Red_yOffsetInches = 0;
        FieldTweaks.field1Red_headingOffsetDeg = 0;
        FieldTweaks.field1Blue_xOffsetInches = 0;
        FieldTweaks.field1Blue_yOffsetInches = 0;
        FieldTweaks.field1Blue_headingOffsetDeg = 0;
        FieldTweaks.field2Red_xOffsetInches = 0;
        FieldTweaks.field2Red_yOffsetInches = 0;
        FieldTweaks.field2Red_headingOffsetDeg = 0;
        FieldTweaks.field2Blue_xOffsetInches = 0;
        FieldTweaks.field2Blue_yOffsetInches = 0;
        FieldTweaks.field2Blue_headingOffsetDeg = 0;
        FieldTweaks.practiceRed_xOffsetInches = 0;
        FieldTweaks.practiceRed_yOffsetInches = 0;
        FieldTweaks.practiceRed_headingOffsetDeg = 0;
        FieldTweaks.practiceBlue_xOffsetInches = 0;
        FieldTweaks.practiceBlue_yOffsetInches = 0;
        FieldTweaks.practiceBlue_headingOffsetDeg = 0;
    }

    private static void assertTriple(AutonFieldTweaks t, double x, double y, double h) {
        assertEquals(x, t.xOffsetInches, 0.0);
        assertEquals(y, t.yOffsetInches, 0.0);
        assertEquals(h, t.headingOffsetDeg, 0.0);
    }

    @Test
    public void everyFieldAndAllianceReturnsItsOwnOffsets() {
        seedDistinctValues();
        assertTriple(FieldTweaks.lookup(true,  FieldTweaks.Field.FIELD_1),  11, 12, 13);
        assertTriple(FieldTweaks.lookup(false, FieldTweaks.Field.FIELD_1),  21, 22, 23);
        assertTriple(FieldTweaks.lookup(true,  FieldTweaks.Field.FIELD_2),  31, 32, 33);
        assertTriple(FieldTweaks.lookup(false, FieldTweaks.Field.FIELD_2),  41, 42, 43);
        assertTriple(FieldTweaks.lookup(true,  FieldTweaks.Field.PRACTICE), 51, 52, 53);
        assertTriple(FieldTweaks.lookup(false, FieldTweaks.Field.PRACTICE), 61, 62, 63);
    }

    /** Untuned is zero offset — the robot must run the authored pose, not drift somewhere. */
    @Test
    public void untunedFieldsApplyNoOffset() {
        assertTriple(FieldTweaks.lookup(true, FieldTweaks.Field.FIELD_1), 0, 0, 0);
        assertTriple(FieldTweaks.lookup(false, FieldTweaks.Field.PRACTICE), 0, 0, 0);
    }

    /** The result must be a fresh object each call: a shared one could be mutated by a caller. */
    @Test
    public void lookupReturnsAnIndependentObject() {
        seedDistinctValues();
        AutonFieldTweaks first = FieldTweaks.lookup(true, FieldTweaks.Field.FIELD_1);
        first.xOffsetInches = -999;
        AutonFieldTweaks second = FieldTweaks.lookup(true, FieldTweaks.Field.FIELD_1);
        assertEquals("a caller's edit leaked into the stored tuning", 11, second.xOffsetInches, 0.0);
    }
}
