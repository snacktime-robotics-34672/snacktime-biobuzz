package org.firstinspires.ftc.teamcode.util;

/**
 * LoopTimer — measures control-loop cycle time (CLAUDE.md section 0 prime directive, section 4 rule 7).
 *
 * The prime directive says a loop-time readout is required, like battery voltage. Every OpMode calls
 * {@link #update()} once per loop and telemeters {@link #getHz()} and {@link #getMaxLoopMs()}. If Hz
 * drops below the section 0 target (100+ Hz / under ~10 ms), treat it as a bug and find the cost.
 *
 * This class follows the directive itself: it allocates NOTHING per loop — only arithmetic on
 * primitives (section 4, rule 8).
 */
public class LoopTimer {

    private long lastNanos = System.nanoTime();
    private double loopMs = 0.0;
    private double hz = 0.0;
    private double maxLoopMs = 0.0;
    private double avgMs = 0.0;

    /** Call once per loop. */
    public void update() {
        long now = System.nanoTime();
        double dtMs = (now - lastNanos) / 1_000_000.0;
        lastNanos = now;

        loopMs = dtMs;
        hz = (dtMs > 0.0) ? (1000.0 / dtMs) : 0.0;
        if (dtMs > maxLoopMs) maxLoopMs = dtMs;
        avgMs = (avgMs == 0.0) ? dtMs : (avgMs * 0.9 + dtMs * 0.1);
    }

    /** Call at match start so the first (large) cycle and stale worst-case don't skew the reading. */
    public void reset() {
        lastNanos = System.nanoTime();
        maxLoopMs = 0.0;
        avgMs = 0.0;
        loopMs = 0.0;
        hz = 0.0;
    }

    /** Most recent single-cycle time, milliseconds. */
    public double getLoopMs() { return loopMs; }

    /** Smoothed cycle rate, Hz — this is the number to watch. */
    public double getHz() { return hz; }

    /** Worst (largest) cycle time since the last reset, ms. A single spike matters. */
    public double getMaxLoopMs() { return maxLoopMs; }

    /** Exponentially-smoothed average cycle time, ms. */
    public double getAverageMs() { return avgMs; }
}
