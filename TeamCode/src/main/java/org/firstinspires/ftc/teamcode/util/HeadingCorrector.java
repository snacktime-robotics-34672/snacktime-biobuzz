package org.firstinspires.ftc.teamcode.util;

import static org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.normalizeRadians;

import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.config.TuningConfig;

/**
 * HeadingCorrector — optional PIDF heading-hold for field-centric TeleOp.
 *
 * WHAT IT DOES: When the driver isn't actively turning (rightX = 0) and a small lag has elapsed,
 * this drives the robot back toward the last commanded heading. Useful for keeping the robot
 * pointed at the target during driving.
 *
 * WHY THE LAG: If the driver just released the turn stick, they may still be finishing a turn
 * physically. Engaging correction immediately would fight residual turn momentum and feel
 * "grabby." The lag lets the robot settle before correction kicks in.
 *
 * VOLTAGE COMPENSATION: PIDF output effectively scales with battery voltage (more volts → more
 * motor authority). Scaling the error by (nominalVoltage / currentVoltage) keeps the corrector's
 * response consistent as the battery drains.
 *
 * USAGE (in TeleOp):
 *   private final HeadingCorrector headingCorrector = new HeadingCorrector();
 *   ...
 *   double turnCommand = -driver.getRightX();
 *   double correctedTurn = headingCorrector.correctHeading(
 *       follower.getPose().getHeading(), turnCommand, batteryVoltage);
 *   follower.setTeleOpDrive(forward, strafe, correctedTurn, false);
 *
 * TUNE ORDER: enable with `TuningConfig.headingCorrectionEnabled = true`. Bump `headingP` until
 * the robot resists rotation. If it oscillates, add `headingD` to damp it. Leave I at 0 unless
 * you see steady-state drift.
 *
 * Ported from FTC 5327 SMS Robotics' decode-2025 (common/drive/HeadingCorrector.java). Adapted:
 * - Removed the inner `Config` static class; values live in `TuningConfig` so they're live-editable
 * - Fixed the "supressed" spelling to "suppressed"
 * - Reads `TuningConfig` fields directly each call — they're Tier-1 configurables, cheap boolean/double reads
 */
public class HeadingCorrector {

    private final ElapsedTime lagTimer;
    private final PIDFController hController = new PIDFController(0, 0, 0, 0);
    private double targetHeading;
    private boolean suppressed = false;

    public HeadingCorrector() {
        this.lagTimer = new ElapsedTime();
        this.targetHeading = 0;
        applyGains();
    }

    private void applyGains() {
        hController.setPIDF(TuningConfig.headingP, TuningConfig.headingI,
                TuningConfig.headingD, TuningConfig.headingF);
    }

    public double getTargetHeading() {
        return targetHeading;
    }

    public void setTargetHeading(double targetHeading) {
        this.targetHeading = targetHeading;
    }

    /**
     * Snap target heading to the nearest multiple of `degreeRounding` degrees — useful for locking
     * onto cardinal directions after a driver-initiated turn.
     */
    public void snapTargetHeadingToNearestDegrees(double degreeRounding) {
        double snappedTargetHeading = Math.toRadians(degreeRounding)
                * Math.round(targetHeading / Math.toRadians(degreeRounding));
        RobotLog.i("Snapping target heading %.3f -> %.3f",
                Math.toDegrees(targetHeading), Math.toDegrees(snappedTargetHeading));
        setTargetHeading(snappedTargetHeading);
    }

    public void reset() {
        lagTimer.reset();
        applyGains();
    }

    /**
     * Main entry point — call once per loop with the current heading, the driver's raw turn command,
     * and the current battery voltage. Returns the (possibly corrected) turn command to feed to the
     * drivetrain.
     *
     * @param currentRobotHeading current robot heading in radians (from Pinpoint via follower.getPose())
     * @param turnSpeed           driver's raw right-stick-X value
     * @param voltage             current battery voltage (for voltage compensation)
     * @return turn command with any heading correction added in
     */
    public double correctHeading(double currentRobotHeading, double turnSpeed, double voltage) {
        if (TuningConfig.headingCorrectionEnabled && !suppressed) {
            if (Math.abs(turnSpeed) > 0) {
                // Driver is actively turning — track the current heading as the new target and
                // hold off correction until they release.
                targetHeading = currentRobotHeading;
                lagTimer.reset();
            }

            if (lagTimer.milliseconds() > TuningConfig.headingCorrectionLagMs) {
                double headingCorrection = computeCorrection(currentRobotHeading, voltage);
                turnSpeed += headingCorrection;
            } else {
                // Still inside the lag window — keep syncing target so we don't fight residual turn.
                targetHeading = currentRobotHeading;
            }
        }
        return turnSpeed;
    }

    private double computeCorrection(double currentRobotHeading, double voltage) {
        double error = normalizeRadians(normalizeRadians(targetHeading) - normalizeRadians(currentRobotHeading));
        // Voltage-compensate: as battery drops, we ask for proportionally more correction to
        // achieve the same physical response.
        error *= TuningConfig.headingNominalVoltage > 0
                ? (TuningConfig.headingNominalVoltage / voltage) : 1;

        // Re-read gains each call so live edits from Panels take effect without restart.
        applyGains();
        double headingCorrection = hController.calculate(0, error);

        // Ignore tiny corrections — avoids twitchy behavior when heading is essentially on-target.
        if (Math.abs(headingCorrection) < TuningConfig.headingCorrectionThresholdMin) {
            headingCorrection = 0;
        }
        return headingCorrection;
    }

    /** Set to true to temporarily disable correction (e.g. during a rotation-during-autonomous). */
    public void setSuppressed(boolean suppressed) {
        if (!this.suppressed && suppressed) {
            RobotLog.w("Heading correction is being suppressed.");
        } else if (this.suppressed && !suppressed) {
            RobotLog.w("Heading correction is no longer suppressed.");
        }
        this.suppressed = suppressed;
    }
}
