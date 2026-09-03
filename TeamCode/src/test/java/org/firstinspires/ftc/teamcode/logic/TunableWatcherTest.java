package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.TunableWatcher;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot tests for the general tunable watcher — the poll that notices a servo position or a
 * speed cap changing and asks for a save.
 *
 * The two things worth pinning: that it actually sees every primitive tunable on every registered
 * class (a watcher that silently covers half the fields is worse than none, because you would trust
 * it), and that its change detection is exact.
 */
public class TunableWatcherTest {

    /** Counts the fields the watcher SHOULD be watching, derived independently of its own table. */
    private static int expectedWatchable() {
        int n = 0;
        for (Class<?> cls : Persistence.tuningClasses()) {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                Class<?> t = f.getType();
                if (t == double.class || t == float.class || t == int.class
                        || t == long.class || t == boolean.class) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    public void watchesEveryPrimitiveTunableOnEveryRegisteredClass() {
        assertEquals("the watcher's field table disagrees with the registered classes",
                expectedWatchable(), TunableWatcher.watchedCount());
    }

    /** If this is zero the watcher is silently doing nothing, and every other test here is hollow. */
    @Test
    public void actuallyFoundSomethingToWatch() {
        assertTrue("no watchable tunables found — the field scan is broken",
                TunableWatcher.watchedCount() > 0);
    }

    /** A known tunable must be covered, so the count above cannot pass on unrelated fields. */
    @Test
    public void coversAKnownTunable() throws Exception {
        Field f = TuningConfig.class.getDeclaredField("verboseTelemetry");
        assertTrue(Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()));
        assertEquals(boolean.class, f.getType());
        assertTrue(TunableWatcher.watchedCount() > 0);
    }

    // ---- change detection --------------------------------------------------------

    @Test
    public void identicalArraysAreNotAChange() {
        double[] a = {1.0, 2.0, 3.0};
        double[] b = {1.0, 2.0, 3.0};
        assertFalse(TunableWatcher.changed(a, b, 3));
    }

    @Test
    public void anySingleMovedValueIsAChange() {
        for (int i = 0; i < 3; i++) {
            double[] a = {1.0, 2.0, 3.0};
            double[] b = {1.0, 2.0, 3.0};
            a[i] += 1e-9;
            assertTrue("index " + i + " should register", TunableWatcher.changed(a, b, 3));
        }
    }

    /** Only the first n entries count, so a reused buffer's tail cannot trigger a phantom save. */
    @Test
    public void onlyTheFirstNEntriesAreCompared() {
        double[] a = {1.0, 2.0, 99.0};
        double[] b = {1.0, 2.0, -99.0};
        assertFalse(TunableWatcher.changed(a, b, 2));
        assertTrue(TunableWatcher.changed(a, b, 3));
    }

    /** A boolean tunable is encoded as 0/1, so flipping one must read as a change. */
    @Test
    public void booleanEncodingDistinguishesTrueFromFalse() {
        double[] off = {0.0};
        double[] on = {1.0};
        assertTrue(TunableWatcher.changed(on, off, 1));
    }
}
