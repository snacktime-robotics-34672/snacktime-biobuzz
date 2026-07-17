package org.firstinspires.ftc.teamcode.util;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import com.qualcomm.robotcore.util.ReadWriteFile;
import org.firstinspires.ftc.teamcode.hardware.BuildInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence — writes/reads the robot's important data as JSON (CLAUDE.md section 7).
 *
 * TWO jobs, one file:
 *   1. AUTO-EXPORT (always on, zero risk): call {@link #writeSnapshot} on every OpMode stop and on
 *      init. The file is a RECORD — it never feeds back in on its own. It is your post-match log,
 *      your traceability record, and the block you copy tuned values from.
 *   2. LOAD-ON-INIT (guarded, optional): {@link #loadTuning} is allowed ONLY if it is loud, falls
 *      back to code defaults on a missing/corrupt file, and the values are promoted into git before
 *      the next session.
 *
 * HARD RULES (CLAUDE.md section 7):
 *   - File I/O NEVER happens in the main loop. Only on init, on stop, or on an explicit button
 *     press. File access is slow and blocking — it would wreck loop time (section 0, section 4).
 *   - git is the real backup, not the hub. A hub re-flash wipes this file.
 */
public final class Persistence {

    private static final String SNAPSHOT_FILE = "snacktime_snapshot.json";
    private static final String TUNING_FILE = "current_tuning.json";
    private static final String TAG = "Persistence";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Everything worth persisting. Extend as the robot grows — keep it a plain data holder. */
    public static class Snapshot {
        public String gitHash = BuildInfo.GIT_HASH;
        public String buildTime = BuildInfo.BUILD_TIME;
        public long savedAtMillis = System.currentTimeMillis();

        // Match context
        public String alliance = "UNKNOWN";        // "RED" / "BLUE"
        public String startPose = "UNKNOWN";       // label of the selected start position
        public String lastKnownGoodPose = "UNKNOWN";

        // Health
        public double startingBatteryVolts = 0.0;
        public boolean systemsCheckPassed = false;
        public List<String> systemsCheckNotes = new ArrayList<>();

        // Tuning values (copied from TuningConfig at save time)
        public Map<String, Double> tuning = new HashMap<>();
    }

    /** AUTO-EXPORT. Safe to call on init and on stop. NEVER in the loop. */
    public static void writeSnapshot(Snapshot snapshot) {
        try {
            File file = AppUtil.getInstance().getSettingsFile(SNAPSHOT_FILE);
            ReadWriteFile.writeFile(file, GSON.toJson(snapshot));
        } catch (Throwable t) {
            // Persistence must never crash the robot. Log and move on.
            Log.e(TAG, "Failed to write snapshot", t);
        }
    }

    /**
     * GUARDED LOAD. Returns parsed tuning only if a valid file exists; otherwise null so the caller
     * keeps code defaults. The CALLER must telemeter loudly when this returns non-null
     * (CLAUDE.md section 7, "it is loud").
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Double> loadTuning() {
        try {
            File file = AppUtil.getInstance().getSettingsFile(TUNING_FILE);
            if (!file.exists()) return null;
            return (Map<String, Double>) GSON.fromJson(ReadWriteFile.readFile(file), Map.class);
        } catch (Throwable t) {
            Log.e(TAG, "Failed/ignored tuning load", t);
            return null;  // corrupt or unreadable -> fall back to code defaults
        }
    }

    private Persistence() { }
}
