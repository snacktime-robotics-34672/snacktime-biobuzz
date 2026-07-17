package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.StaleWatcher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StaleWatcherTest {

    @Test
    public void neverMarked_reportsStaleForAnyThreshold() {
        StaleWatcher watcher = new StaleWatcher("test");
        assertTrue(watcher.isStaleAfterMs(1000));
        assertTrue(watcher.isStaleAfterMs(0));
    }

    @Test
    public void neverMarked_secondsSinceUpdateIsMinusOne() {
        StaleWatcher watcher = new StaleWatcher("test");
        assertEquals(-1.0, watcher.secondsSinceUpdate(), 0.0);
    }

    @Test
    public void justMarked_isNotStaleForGenerousThreshold() {
        StaleWatcher watcher = new StaleWatcher("test");
        watcher.mark();
        assertFalse(watcher.isStaleAfterMs(60_000));
    }

    @Test
    public void nameIsRetained() {
        StaleWatcher watcher = new StaleWatcher("limelight_target");
        assertEquals("limelight_target", watcher.name());
    }

    @Test
    public void secondsSinceUpdate_isPositiveAfterMark() {
        StaleWatcher watcher = new StaleWatcher("test");
        watcher.mark();
        assertTrue("Seconds since update must be >= 0 after mark()",
                watcher.secondsSinceUpdate() >= 0.0);
    }
}