package org.firstinspires.ftc.teamcode.subsystems;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.seattlesolvers.solverslib.command.SubsystemBase;

import org.firstinspires.ftc.teamcode.config.TuningConfig;
import org.firstinspires.ftc.teamcode.diagnostics.DiagnosticsCenter;
import org.firstinspires.ftc.teamcode.diagnostics.Problem;
import org.firstinspires.ftc.teamcode.diagnostics.ProblemSeverity;
import org.firstinspires.ftc.teamcode.hardware.LimelightCamera;
import org.firstinspires.ftc.teamcode.logic.TargetGeometry;
import org.firstinspires.ftc.teamcode.util.StaleWatcher;

/**
 * Vision — where the target is RELATIVE TO THE ROBOT, right now.
 *
 * Not pose. The Pinpoint is the single source of robot pose and nothing here is ever blended into
 * it (CLAUDE.md §3). This subsystem answers one question: is there a target, and how far ahead and
 * to the side of the robot is it.
 *
 * IT OWNS THE CAMERA and reads it exactly ONCE per loop, in {@link #periodic()}, then serves every
 * caller from that one snapshot (§4 rule 2). Callers can ask as often as they like for free.
 *
 * THE CALIBRATION NUMBERS LIVE HERE, as live tunables (§6 Tier 1) rather than constants in an
 * OpMode. That is deliberate and it is the whole point: you tune the camera height and pitch by
 * watching the computed distance against a tape measure, and if those numbers were compiled in you
 * would redeploy for every single trial. Here you turn them in Panels and read the answer on the
 * next loop. Auto reads these same fields, so there is no second copy to keep in step and nothing
 * to transcribe when you are done — you save by committing the robot's tuning file (§6).
 *
 * GRACEFUL DEGRADATION (§5): a target that has gone stale is reported as NO target, not as the last
 * one seen. Something driving toward a ball that is no longer there is worse than something that
 * knows it is blind.
 */
@Configurable
public class Vision extends SubsystemBase {

    // ── Calibration. Tune these by watching VisionCalibration against a tape measure. ────────────

    /** Height of the lens above the floor, inches. */
    public static double cameraHeightInches = 8.0;

    /** How far the camera is tilted DOWN from horizontal, degrees. Always positive. */
    public static double cameraPitchDegrees = 20.0;

    /** Height of the target's center above the floor — for a ball on the floor, its radius. */
    public static double targetCenterHeightInches = 1.875;

    /** Smallest angle below horizontal we will still solve a distance from. See TargetGeometry. */
    public static double minAngleBelowHorizonDeg = 1.0;

    // ── Camera setup ────────────────────────────────────────────────────────────────────────────

    /** Which pipeline on the camera. Changing this in Panels switches it on the next loop. */
    public static int pipeline = 0;

    /** How often the camera is polled. Read once at init; changing it later does nothing. */
    public static int pollRateHz = 100;

    /** A target older than this is treated as no target at all (§5). */
    public static double staleTargetMs = 250.0;

    // ── Problems this subsystem reports ─────────────────────────────────────────────────────────

    public static final Problem CAMERA_DOWN = new Problem(
            "LL_DOWN", "Limelight is not reporting — check power and the USB cable",
            ProblemSeverity.ERROR);

    public static final Problem TARGET_STALE = new Problem(
            "LL_STALE", "Limelight target is stale — using no target instead",
            ProblemSeverity.WARNING);

    private final LimelightCamera camera;
    private final StaleWatcher targetWatcher = new StaleWatcher("limelight_target");

    // One snapshot per loop. All primitives, so periodic() allocates nothing (§4 rule 8).
    private boolean hasTarget = false;
    private double tx = 0.0, ty = 0.0, ta = 0.0;
    private double rangeInches = Double.NaN;
    private double lateralInches = Double.NaN;
    private long stalenessMs = 0;
    private int reportedPipeline = -1;

    // Detection quality, measured over whole frames in a one-second window. NOT over loops: the
    // loop runs faster than the camera, so counting loops would just measure the loop rate.
    private long lastFrameStampNanos = Long.MIN_VALUE;
    private long windowStartNanos = System.nanoTime();
    private int windowFrames = 0, windowValidFrames = 0;
    private double detectionRatePercent = 0.0;
    private double framesPerSecond = 0.0;

    // Problems are reported when the condition ARRIVES, not every loop, so one fault does not
    // become a thousand identical reports.
    private boolean wasConnected = true;
    private boolean wasStale = false;

    public Vision(HardwareMap hardwareMap) {
        camera = new LimelightCamera(hardwareMap);
        camera.start(pipeline, pollRateHz);
    }

    /** Reads the camera once and works out everything callers might ask for. */
    @Override
    public void periodic() {
        camera.switchPipeline(pipeline);

        boolean connected = camera.isConnected();
        if (!connected && wasConnected) DiagnosticsCenter.reportProblem(CAMERA_DOWN);
        wasConnected = connected;

        LLResult result = camera.latestResult();
        countFrame(result);

        stalenessMs = (result == null) ? Long.MAX_VALUE : result.getStaleness();
        boolean validNow = result != null && result.isValid();
        if (validNow) targetWatcher.mark();

        boolean stale = targetWatcher.isStaleAfterMs(staleTargetMs);
        if (validNow && stale && !wasStale) DiagnosticsCenter.reportProblem(TARGET_STALE);
        wasStale = stale;

        hasTarget = validNow && !stale;
        if (!hasTarget) {
            rangeInches = Double.NaN;
            lateralInches = Double.NaN;
            return;
        }

        tx = result.getTx();
        ty = result.getTy();
        ta = result.getTa();
        reportedPipeline = result.getPipelineIndex();

        rangeInches = TargetGeometry.groundRangeInches(
                cameraHeightInches, targetCenterHeightInches,
                cameraPitchDegrees, ty, minAngleBelowHorizonDeg);
        lateralInches = TargetGeometry.lateralOffsetInches(rangeInches, tx);

        // Health detail for the bench, off during matches (§4 rule 8). Numbers, not built strings.
        if (TuningConfig.verboseTelemetry) {
            TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();
            panels.addData("vision range in", rangeInches);
            panels.addData("vision lateral in", lateralInches);
            panels.addData("vision tx", tx);
            panels.addData("vision ty", ty);
        }
    }

    /**
     * Counts whole camera frames, and once a second turns that into a rate.
     *
     * A frame is new when its timestamp changes. Without that check this would count loops, and at
     * 200 Hz against a 100 Hz camera every frame would be counted twice — the number would look
     * healthy no matter what the camera was doing.
     */
    private void countFrame(LLResult result) {
        if (result != null) {
            long stamp = result.getControlHubTimeStampNanos();
            if (stamp != lastFrameStampNanos) {
                lastFrameStampNanos = stamp;
                windowFrames++;
                if (result.isValid()) windowValidFrames++;
            }
        }

        long now = System.nanoTime();
        long elapsed = now - windowStartNanos;
        if (elapsed < 1_000_000_000L) return;

        double seconds = elapsed / 1_000_000_000.0;
        framesPerSecond = windowFrames / seconds;
        detectionRatePercent = (windowFrames == 0) ? 0.0 : (100.0 * windowValidFrames / windowFrames);
        windowFrames = 0;
        windowValidFrames = 0;
        windowStartNanos = now;
    }

    /** True when a fresh, valid target is in view right now. */
    public boolean hasTarget() { return hasTarget; }

    /** Distance along the floor to the target, inches. NaN when there is no usable target. */
    public double rangeInches() { return rangeInches; }

    /** How far the target sits to the side, inches, positive LEFT. NaN when there is none. */
    public double lateralOffsetInches() { return lateralInches; }

    /** Horizontal angle to the target, degrees, positive right. Raw from the camera. */
    public double tx() { return tx; }

    /** Vertical angle to the target, degrees, positive up. Raw from the camera. */
    public double ty() { return ty; }

    /** How much of the frame the target fills, percent. Useful for sanity, not for distance. */
    public double ta() { return ta; }

    /** Age of the newest result, milliseconds. */
    public long stalenessMs() { return stalenessMs; }

    /** Share of camera frames that found a target, over the last second. */
    public double detectionRatePercent() { return detectionRatePercent; }

    /** Whole camera frames per second, over the last second. Should sit near pollRateHz. */
    public double framesPerSecond() { return framesPerSecond; }

    /** The pipeline the CAMERA says it is running — not the one we asked for. */
    public int reportedPipeline() { return reportedPipeline; }

    public boolean isConnected() { return camera.isConnected(); }

    /** Milliseconds since the camera sent anything at all. */
    public long msSinceLastUpdate() { return camera.msSinceLastUpdate(); }

    public void stop() {
        camera.stop();
    }
}
