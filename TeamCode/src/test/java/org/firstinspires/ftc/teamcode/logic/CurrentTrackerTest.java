package org.firstinspires.ftc.teamcode.logic;

import static org.junit.Assert.assertEquals;

import org.firstinspires.ftc.teamcode.util.CurrentTracker;
import org.junit.Test;

/** Tests for the drive-current max/mean tracker (CLAUDE.md §9). */
public class CurrentTrackerTest {

    @Test
    public void reportsZeroBeforeAnythingIsSampled() {
        CurrentTracker t = new CurrentTracker();
        assertEquals(0.0, t.getMax(), 1e-9);
        assertEquals(0.0, t.getMean(), 1e-9);
        assertEquals(0L, t.getSamples());
    }

    @Test
    public void tracksMaxAndMean() {
        CurrentTracker t = new CurrentTracker();
        t.add(2.0);
        t.add(10.0);
        t.add(3.0);
        assertEquals(10.0, t.getMax(), 1e-9);
        assertEquals(5.0, t.getMean(), 1e-9);
        assertEquals(3.0, t.getLast(), 1e-9);
        assertEquals(3L, t.getSamples());
    }

    /** The spike is the point: a later smaller reading must not lower the max. */
    @Test
    public void maxHoldsAfterTheSpikePasses() {
        CurrentTracker t = new CurrentTracker();
        t.add(18.0);
        t.add(1.0);
        assertEquals(18.0, t.getMax(), 1e-9);
        assertEquals(1.0, t.getLast(), 1e-9);
    }

    /** A failed hardware read must not poison the whole session's numbers. */
    @Test
    public void ignoresNaNReadings() {
        CurrentTracker t = new CurrentTracker();
        t.add(4.0);
        t.add(Double.NaN);
        assertEquals(4.0, t.getMax(), 1e-9);
        assertEquals(4.0, t.getMean(), 1e-9);
        assertEquals(1L, t.getSamples());
    }

    @Test
    public void resetClearsEverything() {
        CurrentTracker t = new CurrentTracker();
        t.add(9.0);
        t.reset();
        assertEquals(0.0, t.getMax(), 1e-9);
        assertEquals(0.0, t.getMean(), 1e-9);
        assertEquals(0L, t.getSamples());
        t.add(2.0);
        assertEquals(2.0, t.getMean(), 1e-9);
    }
}
