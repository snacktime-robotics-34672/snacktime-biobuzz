package org.firstinspires.ftc.teamcode.util;

import android.util.Log;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Datalogger — appends chosen signals to a CSV for time-series debugging (CLAUDE.md section 14).
 *
 * Different from the other two data tools:
 *   - Persistence (section 7) writes ONE snapshot at start/stop.
 *   - Live telemetry (section 8) shows the current instant on a dashboard.
 *   - Datalogger writes EVERY loop, so you can plot the whole run afterward and find the exact
 *     instant a value spiked.
 *
 * LOOP-TIME NOTE (sections 0 and 4): logging every loop looks like it breaks the "no file I/O in
 * the loop" rule (section 7). It doesn't — IF used correctly. The writer is BUFFERED, so a per-loop
 * {@link #log} is a cheap in-memory append; the real (slow) disk write is batched and happens on
 * {@link #stop}. So:
 *   - open once in init, NEVER flush per loop, close on stop,
 *   - keep the column count small,
 *   - treat it as a bench/diagnostic tool you switch on to investigate — not always-on in matches.
 *
 * Usage:
 *   private final Datalogger log = new Datalogger("loop_debug.csv", "hz", "worstMs", "intakeMode");
 *   // init:  log.start();
 *   // loop:  log.log(loopTimer.getHz(), loopTimer.getMaxLoopMs(), intake.getMode());
 *   // stop:  log.stop();
 * Pull the CSV off the hub (ADB or the Manage page) and plot it.
 */
public class Datalogger {

    private static final String TAG = "Datalogger";

    private final String fileName;
    private final String[] columns;
    private final StringBuilder row = new StringBuilder(); // reused to limit allocation (rule 8)
    private BufferedWriter writer;

    public Datalogger(String fileName, String... columns) {
        this.fileName = fileName;
        this.columns = columns;
    }

    /** Call once at init. Opens the file (overwriting any previous run) and writes the header. */
    public void start() {
        try {
            File file = AppUtil.getInstance().getSettingsFile(fileName);
            BufferedWriter w = new BufferedWriter(new FileWriter(file, false));
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) w.write(",");
                w.write(columns[i]);
            }
            w.newLine();
            writer = w;
        } catch (Throwable t) {
            Log.e(TAG, "open failed", t);
            writer = null;
        }
    }

    /** Call once per loop with one value per column, in header order. Cheap (buffered). */
    public void log(Object... values) {
        if (writer == null) return;
        try {
            row.setLength(0);
            for (int i = 0; i < values.length; i++) {
                if (i > 0) row.append(',');
                row.append(values[i]);
            }
            writer.write(row.toString());
            writer.newLine();
            // Intentionally NOT flushing here — flushing every loop is the slow part.
        } catch (Throwable t) {
            Log.e(TAG, "write failed", t);
        }
    }

    /** Call on stop. Flushes and closes — this is where the real disk I/O happens. */
    public void stop() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (Throwable t) {
            Log.e(TAG, "close failed", t);
        } finally {
            writer = null;
        }
    }
}
