package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.firstinspires.ftc.teamcode.util.Persistence;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Off-robot tests for restoring tuning values onto static fields (CLAUDE.md §9).
 *
 * Saving a tunable always works — it is a plain reflective read and GSON writes whatever it finds.
 * Restoring is the half that has to know the type, so this is the only place a tuned value can go
 * missing. It used to fail silently: an unsupported type saved to the file, looked right in the
 * JSON, and never came back, while the "LOADED ... N values" banner counted it as restored. These
 * tests pin down both halves of the fix — the types we handle, and that anything else reports
 * itself instead of vanishing.
 *
 * GSON deserializes every JSON number as Double, which is why the numeric cases below feed in
 * Doubles and expect the field's real type back.
 */
public class PersistenceApplyFieldTest {

    public enum Mode { SLOW, FAST }

    /** Stand-in for a @Configurable tunables class. */
    public static class Holder {
        public static double d;
        public static float f;
        public static boolean b;
        public static long l;
        public static int i;
        public static String s;
        public static Mode mode;
        public static int[] unsupported;
    }

    private static Field field(String name) throws Exception {
        return Holder.class.getDeclaredField(name);
    }

    // ---- Numbers arrive from GSON as Double and must land as the field's own type --------------

    @Test
    public void restoresDouble() throws Exception {
        assertTrue(Persistence.applyToField(field("d"), 12.5));
        assertEquals(12.5, Holder.d, 1e-9);
    }

    @Test
    public void restoresFloat() throws Exception {
        assertTrue(Persistence.applyToField(field("f"), 2.25));
        assertEquals(2.25f, Holder.f, 1e-6);
    }

    @Test
    public void restoresLong() throws Exception {
        assertTrue(Persistence.applyToField(field("l"), 900.0));
        assertEquals(900L, Holder.l);
    }

    @Test
    public void restoresIntFromADouble() throws Exception {
        assertTrue(Persistence.applyToField(field("i"), 7.0));
        assertEquals(7, Holder.i);
    }

    @Test
    public void restoresBoolean() throws Exception {
        assertTrue(Persistence.applyToField(field("b"), true));
        assertTrue(Holder.b);
    }

    // ---- The two that used to disappear --------------------------------------------------------

    @Test
    public void restoresString() throws Exception {
        assertTrue(Persistence.applyToField(field("s"), "comp"));
        assertEquals("comp", Holder.s);
    }

    @Test
    public void restoresEnumByName() throws Exception {
        assertTrue(Persistence.applyToField(field("mode"), "FAST"));
        assertEquals(Mode.FAST, Holder.mode);
    }

    // ---- Anything else must report itself, not vanish -------------------------------------------

    @Test
    public void reportsUnsupportedTypeInsteadOfSilentlySkipping() throws Exception {
        assertFalse(Persistence.applyToField(field("unsupported"), "whatever"));
    }

    @Test
    public void unknownEnumConstantThrowsRatherThanCorrupting() throws Exception {
        // A constant renamed or removed since the file was written. The caller catches this, logs
        // it, and leaves the code default in place — the safe direction.
        Holder.mode = Mode.SLOW;
        try {
            Persistence.applyToField(field("mode"), "TURBO");
            org.junit.Assert.fail("expected an exception for an unknown enum constant");
        } catch (IllegalArgumentException expected) {
            assertEquals("field must keep its code default", Mode.SLOW, Holder.mode);
        }
    }
}
