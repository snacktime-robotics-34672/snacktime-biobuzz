package org.firstinspires.ftc.teamcode.util;

import com.qualcomm.robotcore.util.RobotLog;

/**
 * TuningAutosave — the one place a tuning-file write is asked for, and the thread that does it.
 *
 * WHY IT IS SEPARATE: two watchers ask for saves — {@code TuningRecorder} for Pedro constants and
 * {@link TunableWatcher} for every other tunable. They must share one queue and one writer thread,
 * or a Pedro change and a servo change a moment apart would race each other writing the same file.
 *
 * WHY A THREAD AT ALL: file writes are slow and blocking, and CLAUDE.md §4 rule 3 says the loop
 * never blocks. The loop's whole involvement is {@link #request}, which sets a flag and returns.
 *
 * WHY wait/notify AND NOT A SLEEP LOOP: a polling writer wakes several times a second forever, even
 * when nobody has tuned anything all match. Waiting on a monitor costs exactly nothing while idle
 * and starts the write the instant it is asked for, instead of up to a tick later.
 */
public final class TuningAutosave {

    private static final String TAG = "TuningAutosave";
    private static final Object LOCK = new Object();

    private static boolean pending = false;
    private static RobotIdentity identity = null;
    private static Thread writer = null;

    private TuningAutosave() {}

    /**
     * Asks for this robot's tuning file to be written. Returns immediately; safe from the loop.
     *
     * The flag is the entire queue. Several requests inside one write coalesce into a single save,
     * and that save picks up the newest values because the writer reads the live statics rather
     * than a copy captured when the request was made.
     */
    public static void request(RobotIdentity id) {
        if (id == null) return;
        synchronized (LOCK) {
            identity = id;
            pending = true;
            startOnce();
            LOCK.notifyAll();
        }
    }

    /** Caller must hold LOCK. Daemon + lowest priority so it always yields to the control loop. */
    private static void startOnce() {
        if (writer != null) return;
        writer = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    RobotIdentity id;
                    synchronized (LOCK) {
                        while (!pending) {
                            try {
                                LOCK.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        pending = false;
                        id = identity;
                    }
                    try {
                        if (id != null) Persistence.saveTuning(id);
                    } catch (Throwable t) {
                        // A failed autosave must never kill the thread — the next change should
                        // still get a chance to write, and the PEDRO_TUNED log block is the
                        // human-readable backstop if writes keep failing.
                        RobotLog.ee(TAG, "autosave failed: %s", t);
                    }
                }
            }
        }, "TuningAutosave");
        writer.setDaemon(true);
        writer.setPriority(Thread.MIN_PRIORITY);
        writer.start();
    }
}
