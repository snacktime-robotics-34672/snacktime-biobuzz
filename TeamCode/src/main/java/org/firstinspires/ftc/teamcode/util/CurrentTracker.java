package org.firstinspires.ftc.teamcode.util;

/**
 * CurrentTracker — running max and mean of a current reading (CLAUDE.md §9, testable off-robot).
 *
 * Deliberately dumb: you hand it a number each loop and it remembers the largest one and the
 * average. It knows nothing about motors, so it is unit-tested without a robot, and it can watch any
 * signal later — a lift, an intake — not just the drivetrain.
 *
 * MAX AND MEAN ANSWER DIFFERENT QUESTIONS. Max is the spike: did we stall a wheel, did we hit a
 * wall, did we brown the battery out. Mean is the load: how hard is this robot working over a whole
 * match, which is what tells you whether the battery will last. Watching only one of them hides half
 * the story.
 *
 * The mean is over the whole session, not a rolling window, so sitting still drags it down. That is
 * the honest number for "what did this match cost us"; use max for the moment-to-moment.
 *
 * Allocates nothing (§4 rule 8) — four primitives and arithmetic.
 */
public class CurrentTracker {

    private double last = 0.0;
    private double max = 0.0;
    private double sum = 0.0;
    private long samples = 0L;

    /** Call once per reading. */
    public void add(double amps) {
        // A failed hardware read comes back as a placeholder rather than an exception, and NaN would
        // poison both the max and the mean for the rest of the session. Drop it instead.
        if (Double.isNaN(amps)) return;
        last = amps;
        if (amps > max) max = amps;
        sum += amps;
        samples++;
    }

    /** The most recent reading. */
    public double getLast() { return last; }

    /** The largest reading since the last reset. */
    public double getMax() { return max; }

    /** The average of every reading since the last reset, or 0 before the first one. */
    public double getMean() { return samples == 0L ? 0.0 : sum / samples; }

    /** How many readings went in. Zero means nothing has been sampled — treat max/mean as unknown. */
    public long getSamples() { return samples; }

    /** Clears the history. Call at START so the init-time readings do not skew the match numbers. */
    public void reset() {
        last = 0.0;
        max = 0.0;
        sum = 0.0;
        samples = 0L;
    }
}
