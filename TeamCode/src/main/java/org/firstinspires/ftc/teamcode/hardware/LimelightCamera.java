package org.firstinspires.ftc.teamcode.hardware;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * LimelightCamera — thin typed wrapper around the Limelight 3A (CLAUDE.md §3, layer 1).
 *
 * This is the ONLY place that asks the hardwareMap for the camera. Everything above it works with
 * the values the camera reports, so a change to wiring or naming stops here.
 *
 * ALL DETECTION RUNS ON THE CAMERA (§4 rule 4). The Control Hub never runs inference. A background
 * thread inside the SDK polls the camera over USB, so {@link #latestResult()} only reads a field
 * that thread already filled in — it does not touch the network and does not block the loop
 * (§4 rule 3). Calling it more than once per loop is cheap but pointless: the answer cannot change
 * inside one cycle, so the Vision subsystem reads it once and caches (§4 rule 2).
 *
 * FAIL LOUD (§5): if the camera is missing from the robot configuration, the constructor says so by
 * name instead of letting the OpMode start with a camera that will never report anything.
 */
public class LimelightCamera {

    /**
     * The camera's name in the Robot Controller configuration. It must match on BOTH robots so the
     * same commit runs unmodified on each (§10).
     */
    public static final String CONFIG_NAME = "limelight";

    private final Limelight3A limelight;
    private int currentPipeline = -1;

    public LimelightCamera(HardwareMap hardwareMap) {
        try {
            limelight = hardwareMap.get(Limelight3A.class, CONFIG_NAME);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "No Limelight named \"" + CONFIG_NAME + "\" in the robot configuration. "
                            + "Add it on the Driver Hub (Configure Robot -> USB devices) with exactly "
                            + "that name, on this robot and on the other one.", e);
        }
    }

    /** Starts polling. Call once at init, never in the loop. */
    public void start(int pipeline, int pollRateHz) {
        switchPipeline(pipeline);
        limelight.setPollRateHz(pollRateHz);
        limelight.start();
    }

    public void stop() {
        limelight.stop();
    }

    /**
     * Selects a pipeline, and only talks to the camera when the number actually changes — the write
     * goes out over the network, so doing it every loop would spend loop time to say nothing new.
     */
    public void switchPipeline(int pipeline) {
        if (pipeline == currentPipeline) return;
        limelight.pipelineSwitch(pipeline);
        currentPipeline = pipeline;
    }

    /**
     * The most recent result the poller has received. Never null: the SDK returns an empty result
     * before the first frame arrives, so check {@link LLResult#isValid()}, not null.
     */
    public LLResult latestResult() {
        return limelight.getLatestResult();
    }

    /** True while the camera has reported recently. False means unplugged, unpowered or booting. */
    public boolean isConnected() {
        return limelight.isConnected();
    }

    /** True once {@link #start} has been called and polling is running. */
    public boolean isRunning() {
        return limelight.isRunning();
    }

    /** Milliseconds since the camera last sent anything at all, target or no target. */
    public long msSinceLastUpdate() {
        return limelight.getTimeSinceLastUpdate();
    }
}
