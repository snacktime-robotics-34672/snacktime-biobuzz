package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.telemetry.PanelsTelemetry;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.CommandScheduler;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.subsystems.Vision;
import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * VisionCalibration — prove the camera math before anything drives on it.
 *
 * It reads the Limelight and prints the distance the geometry computes, so you can hold it against
 * a tape measure. It drives nothing. A bad camera transform looks exactly like a badly tuned path
 * follower from the driver station, so proving this layer first saves you from tuning Pedro to
 * cover for a trigonometry error.
 *
 * ── PROCEDURE ───────────────────────────────────────────────────────────────────────────────────
 *  1. Robot stationary on the field tiles. Open Panels.
 *  2. Put the ball straight in front of the camera at a measured distance. Measure along the floor,
 *     from the point directly under the lens to the point directly under the center of the ball.
 *  3. Compare "Range in" against the tape. Repeat at 24, 36, 48 and 72 inches.
 *  4. Too LARGE at every distance means cameraPitchDegrees is too small — the camera is really
 *     aimed further down than you told it. Too SMALL means the pitch is too large.
 *  5. Turn Vision.cameraHeightInches and Vision.cameraPitchDegrees IN PANELS until the error is
 *     under about 2 inches across the range. They take effect on the next loop; there is nothing to
 *     redeploy and nothing to copy anywhere afterwards — auto reads these same fields (§6).
 *  6. Check the sign: put the ball clearly to the LEFT and confirm "Lateral in" goes positive.
 *
 * ── WHAT ELSE TO WATCH ──────────────────────────────────────────────────────────────────────────
 * "Detect %" is the share of CAMERA FRAMES that found the ball over the last second. If it flickers
 * while the ball sits still, the problem is upstream in the pipeline on the camera, not in this
 * math. "FPS" should sit near Vision.pollRateHz; well under it means the camera is struggling or
 * the link is poor.
 */
@TeleOp(name = "Vision Calibration", group = "Tuning")
public class VisionCalibration extends CommandOpMode {

    private final LoopTimer loopTimer = new LoopTimer();
    private BulkReads bulkReads;
    private Vision vision;
    private RobotIdentity robotId;
    private String idBanner;
    private String idBannerHtml;

    @Override
    public void initialize() {
        // Rule 1 applies even here. Nothing in this OpMode reads a hub sensor today, so the clear
        // costs nothing and protects whoever adds the first one (§4).
        bulkReads = new BulkReads(hardwareMap);

        robotId = RobotIdentity.resolve();
        idBanner = robotId.banner();
        idBannerHtml = robotId.bannerHtml();
        telemetry.setDisplayFormat(Telemetry.DisplayFormat.HTML);

        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry);

        // Throws by name if the camera is missing from the configuration, rather than starting
        // blind and reporting "no target" forever (§5).
        vision = new Vision(hardwareMap);

        telemetry.addLine("Vision calibration ready.");
        telemetry.addLine("Put the ball at a measured distance, then press START.");
        telemetry.addLine("Tune cameraHeightInches / cameraPitchDegrees under \"Vision\" in Panels.");
    }

    @Override
    public void run() {
        bulkReads.clear();

        // Runs Vision.periodic(), which reads the camera once for the whole loop (§4 rule 2).
        super.run();

        loopTimer.update();

        telemetry.addLine(idBannerHtml);
        PanelsTelemetry.INSTANCE.getTelemetry().debug(idBanner);
        telemetry.addData("Loop Hz", loopTimer.getHz());
        telemetry.addData("Worst ms", loopTimer.getMaxLoopMs());

        telemetry.addData("Camera", vision.isConnected() ? "connected" : "*** NOT CONNECTED ***");
        telemetry.addData("Target", vision.hasTarget() ? "YES" : "no");

        if (vision.hasTarget()) {
            telemetry.addData("Range in", vision.rangeInches());
            telemetry.addData("Lateral in (+left)", vision.lateralOffsetInches());
            telemetry.addData("tx deg", vision.tx());
            telemetry.addData("ty deg", vision.ty());
            telemetry.addData("ta %", vision.ta());
            telemetry.addData("Stale ms", vision.stalenessMs());
            // NaN means the geometry could not be solved, which is nearly always a pitch sign or a
            // camera height below the ball center — say which, rather than printing NaN alone.
            if (Double.isNaN(vision.rangeInches())) {
                telemetry.addLine("Range is NaN: ball is at or above the horizon. Check that "
                        + "cameraPitchDegrees is POSITIVE and larger than ty, and that "
                        + "cameraHeightInches is above targetCenterHeightInches.");
            }
        }

        telemetry.addData("Pipeline", vision.reportedPipeline());
        telemetry.addData("Detect %", vision.detectionRatePercent());
        telemetry.addData("FPS", vision.framesPerSecond());
        telemetry.update();
    }

    @Override
    public void reset() {
        vision.stop();
        Persistence.saveTuning(robotId);
        Persistence.Snapshot stopSnap = new Persistence.Snapshot();
        stopSnap.robot = robotId.robot.name();
        stopSnap.networkName = robotId.networkName;
        stopSnap.captureLoop(loopTimer);
        Persistence.writeSnapshot(stopSnap, hardwareMap);
        CommandScheduler.getInstance().reset();
    }
}
