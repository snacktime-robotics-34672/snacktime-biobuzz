package org.firstinspires.ftc.teamcode.util;

import static java.util.Objects.isNull;

import com.qualcomm.robotcore.util.MovingStatistics;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.config.TuningConfig;

import java.util.HashMap;

/**
 * Profiler — measures how long a block of code takes and tracks rolling avg/min/max.
 *
 * WHY: `LoopTimer` tells us the whole loop's Hz. When the loop drops below the §0 target and it
 * isn't obvious why, Profiler tells us WHICH subsystem or command is eating the budget. Wrap
 * suspected hot code:
 *
 *   Profiler.timeIt("intake.periodic", () -> intake.periodic());
 *
 * When `TuningConfig.profilerEnabled` is true, the RobotLog line shows current ms, moving mean,
 * min, and max for that name. Turn it OFF for matches — the RobotLog write is I/O and adds to the
 * loop budget (§4 rule 8).
 *
 * Ported from FTC 5327 SMS Robotics' decode-2025 (common/measurement/Profiler.java). Adapted:
 * - `GlobalConfig.PROFILER_ENABLED` → `TuningConfig.profilerEnabled` (our Tier-1 configurable)
 * - Diamond-operator warnings fixed (`new HashMap()` → `new HashMap<>()`)
 */
public class Profiler {
    private static final HashMap<String, MovingStatistics> stats = new HashMap<>();
    private static final HashMap<String, Double> minValues = new HashMap<>();
    private static final HashMap<String, Double> maxValues = new HashMap<>();

    public static void timeIt(String name, Runnable runnable) {
        long tic = System.nanoTime();
        runnable.run();
        long toc = System.nanoTime();

        // Fast bailout when profiling is off — no map lookups, no allocation, no log I/O.
        // Cost is one boolean read.
        if (!TuningConfig.profilerEnabled) {
            return;
        }

        double ms = (toc - tic) / 1.0e6;

        MovingStatistics movingStatistics = stats.getOrDefault(name, null);
        if (isNull(movingStatistics)) {
            movingStatistics = new MovingStatistics(20);
            stats.put(name, movingStatistics);
            minValues.put(name, ms);
            maxValues.put(name, ms);
        }

        movingStatistics.add(ms);
        minValues.computeIfPresent(name, (keyName, previousMin) -> Math.min(previousMin, ms));
        maxValues.computeIfPresent(name, (keyName, previousMax) -> Math.max(previousMax, ms));

        double min = minValues.get(name);
        double max = maxValues.get(name);

        RobotLog.i("Time %s: %4.2fms | avg=%4.2f, min=%4.2f, max=%4.2f",
                name, ms, movingStatistics.getMean(), min, max);
    }

    private Profiler() { } // static holder; never instantiated
}
