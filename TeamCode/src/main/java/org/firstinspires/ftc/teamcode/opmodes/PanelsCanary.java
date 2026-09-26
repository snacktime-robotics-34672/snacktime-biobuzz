package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.diagnostics.PanelsProbe;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * PanelsCanary — proves that live tuning actually reaches the robot (CLAUDE.md §2, §6 Tier 1).
 *
 * HOW TO RUN IT: start this OpMode, open Panels, find "PanelsProbe" and type a number into
 * {@code probe}. The "CANARY probe" line below must change to that number within a second.
 *   - It changes  → Panels is writing into the same class the robot reads. Live tuning works, and
 *                   every other tunable on the robot can be trusted.
 *   - It does NOT → live tuning is dead and EVERY tunable is silently lying to you. Stop and fix
 *                   it before tuning anything. Check that TeamCode/build.gradle still uses the
 *                   SLOTH forks: com.bylazar.sloth:fullpanels, never com.bylazar:fullpanels.
 *
 * WHY IT NEEDS ITS OWN OPMODE: this check has to be done while something is running and reading the
 * value, and it must not be buried in a match OpMode where nobody would look. Reading one static
 * and printing it is the entire job.
 *
 * ADDED 2026-09-23. {@link PanelsProbe} existed for a long time before this, but nothing anywhere
 * displayed it — so the canary CLAUDE.md §2 tells you to check could not actually be checked. That
 * is the same shape of silent failure the probe exists to catch, one level up.
 */
@TeleOp(name = "34672 Panels Canary", group = "diagnostics")
public class PanelsCanary extends LinearOpMode {

    @Override
    public void runOpMode() {
        TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();

        telemetry.addLine(RobotIdentity.resolve().banner());
        telemetry.addLine("Press START, then type a number into PanelsProbe.probe in Panels.");
        telemetry.addLine("The CANARY line must follow what you type. If it does not, live");
        telemetry.addLine("tuning is broken and every tunable on this robot is lying.");
        telemetry.update();

        waitForStart();

        while (opModeIsActive()) {
            // No bulk cache clear: this OpMode reads no hardware at all, so there is nothing to go
            // stale. The one value it reads is the static Panels writes into.
            double value = PanelsProbe.probe;

            telemetry.addData("CANARY probe", value);
            telemetry.addLine(value == 0.0
                    ? "Type a number into PanelsProbe.probe in Panels..."
                    : "LIVE TUNING WORKS — Panels reached the robot.");
            telemetry.update();

            // Mirrored to Panels so you can watch it on the same screen you are typing into.
            panels.addData("CANARY probe", value);
            panels.update();
        }
    }
}
