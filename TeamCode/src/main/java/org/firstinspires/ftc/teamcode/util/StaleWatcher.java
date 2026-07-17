package org.firstinspires.ftc.teamcode.util;

/**
 * StaleWatcher — tracks when a value was last updated and reports whether it's gone stale.
 *
 * USE FOR: catching sensors or subsystems that silently stop updating. Example: the Limelight is
 * supposed to publish a target every ~50ms; if we haven't seen one in 500ms it's probably
 * disconnected, and we should degrade gracefully (CLAUDE.md §5) instead of pretending the last
 * target is still valid.
 *
 * USAGE:
 *   private final StaleWatcher targetWatcher = new StaleWatcher("limelight_target");
 *
 *   // when a fresh reading arrives:
 *   targetWatcher.mark();
 *
 *   // in the subsystem's periodic() or health check:
 *   if (targetWatcher.isStaleAfterMs(500)) {
 *       DiagnosticsCenter.reportProblem(LIMELIGHT_STALE);
 *   }
 *
 * WHY IT'S THIS SMALL: The concept is stolen from decode-2025's DataBus.Seat (write timestamp +
 * hasChangedSinceSeconds), but without the global registry, atomics, or supplier factories. We're
 * single-threaded on the loop, so `long` is sufficient — no need for AtomicLong.
 */
public class StaleWatcher {

    private final String name;
    private long lastUpdateNanos = -1;

    public StaleWatcher(String name) {
        this.name = name;
    }

    /** Call whenever a fresh value arrives from the underlying source. */
    public void mark() {
        lastUpdateNanos = System.nanoTime();
    }

    /**
     * @return seconds since last mark(). Returns -1 if mark() has never been called (so callers can
     *         distinguish "never updated" from "just updated").
     */
    public double secondsSinceUpdate() {
        if (lastUpdateNanos < 0) return -1;
        return (System.nanoTime() - lastUpdateNanos) * 1e-9;
    }

    /**
     * @param ms threshold in milliseconds
     * @return true if either (a) mark() has never been called, or (b) it's been longer than ms
     *         since the last mark(). Both conditions mean "don't trust the last value."
     */
    public boolean isStaleAfterMs(double ms) {
        if (lastUpdateNanos < 0) return true;
        return secondsSinceUpdate() * 1000.0 > ms;
    }

    public String name() {
        return name;
    }
}