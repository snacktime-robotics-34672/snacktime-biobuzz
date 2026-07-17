package org.firstinspires.ftc.teamcode.util;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.List;

/**
 * BulkReads — enforces the bulk-read discipline (CLAUDE.md section 4, rules 1 and 2).
 *
 * WHY THIS EXISTS: SolversLib does NOT manage bulk caching for you. This is ours to own, and it is
 * the single biggest lever on loop time (the prime directive, section 0), because the loop budget
 * is dominated by hardware I/O, not math.
 *
 * MANUAL caching means the hub reads ALL sensors in one transaction, and every subsequent read in
 * that loop is free — but the cache goes STALE until you clear it. So the rule is absolute:
 *
 *     clear() is the FIRST line of every loop. Every loop. Always.
 *
 * Forgetting it means you silently read last loop's sensor values, which is one of the nastiest
 * bugs in FTC — the robot behaves oddly and nothing looks wrong in the code.
 *
 * Usage:
 *   // in initialize():   bulkReads = new BulkReads(hardwareMap);
 *   // first line of loop: bulkReads.clear();
 */
public class BulkReads {

    private final List<LynxModule> hubs;

    /** Puts every hub into MANUAL bulk-caching mode. Call once during init. */
    public BulkReads(HardwareMap hardwareMap) {
        hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) {
            hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
        }
    }

    /**
     * Clears the cache so this loop's reads hit the hardware exactly once.
     * MUST be the first thing in the loop (CLAUDE.md section 4, rule 1).
     */
    public void clear() {
        // Indexed loop, not for-each: avoids allocating an iterator every cycle (section 4, rule 8).
        for (int i = 0; i < hubs.size(); i++) {
            hubs.get(i).clearBulkCache();
        }
    }
}
