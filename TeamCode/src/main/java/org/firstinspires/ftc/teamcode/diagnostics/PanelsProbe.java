package org.firstinspires.ftc.teamcode.diagnostics;

import com.bylazar.configurables.annotations.Configurable;

/**
 * PanelsProbe — the canary for Panels live tuning (CLAUDE.md §6 Tier 1).
 *
 * One bare double with nothing between it and the dashboard: no nesting, no library types, no
 * object graph. Type a number into {@code probe} in Panels and the "Panels canary" line in the
 * Tuning telemetry should move on the next loop. If it does not, live tuning is broken and every
 * other tunable on the robot is quietly lying to you.
 *
 * WHY THIS EXISTS. Live tuning was dead for a long time and nothing said so. Stock Panels finds
 * tunables by scanning the installed APK and resolving each class with {@code Class.forName}, which
 * returns ITS copy of the class — not the copy Sloth loaded and the robot is running. Two classes,
 * same name, separate statics. Panels wrote one, the robot read the other, and nothing logged an
 * error, because nothing failed: the value just landed where nothing looks. Measured on the test
 * bot 2026-08-30 — typing 50 gave 50.0 through Panels' handle and 0.0 through the robot's, under
 * PathClassLoader and SlothClassLoader.
 *
 * The fix was the Sloth build of Panels ({@code com.bylazar.sloth:fullpanels}, see
 * {@code TeamCode/build.gradle}), which registers classes through Sinister — Sloth hands it the
 * real class and loader — so only one copy ever exists. This probe stays as the cheap check that
 * it is still true, especially after any Panels or Sloth version bump.
 */
@Configurable
public class PanelsProbe {
    public static double probe = 0.0;
}
