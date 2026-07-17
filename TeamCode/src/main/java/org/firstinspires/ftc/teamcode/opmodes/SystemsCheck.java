package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.firstinspires.ftc.teamcode.util.Persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * SystemsCheck — the pre-match diagnostic OpMode (CLAUDE.md section 5).
 *
 * Run this BEFORE every match. It confirms each motor is present and configured, then pulses each
 * one so a human can verify the RIGHT mechanism moves. A wiring or config fault (a dead motor, a
 * swapped port — the kind of thing the back-left slip was) shows up on the bench instead of
 * mid-match. The result is stamped into a snapshot (section 7).
 *
 * Written as a plain LinearOpMode ON PURPOSE: it must still work even if the framework layer is
 * broken. That's the whole point of a diagnostic.
 */
@TeleOp(name = "34672 Systems Check", group = "diagnostics")
public class SystemsCheck extends LinearOpMode {

    private static final String[] MOTOR_NAMES = {
            "front_left", "front_right", "back_left", "back_right", "intake_motor"
    };

    @Override
    public void runOpMode() {
        List<String> notes = new ArrayList<>();
        boolean passed = true;

        // --- check each motor is present and configured ---
        for (String name : MOTOR_NAMES) {
            boolean ok;
            try {
                hardwareMap.get(DcMotorEx.class, name);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            notes.add(ok ? ("OK   motor '" + name + "' found")
                         : ("FAIL motor '" + name + "' MISSING"));
            if (!ok) passed = false;
        }

        // TODO: add sensor checks — Limelight reachable, Pinpoint responding, rangefinder reading.

        // --- battery voltage is a cheap, high-value pre-match check ---
        double volts = 0.0;
        try {
            volts = hardwareMap.voltageSensor.iterator().next().getVoltage();
            notes.add(String.format("Battery: %.2f V", volts));
            if (volts < 12.0) {
                notes.add("WARN battery is low — charge before the match");
            }
        } catch (Throwable t) {
            notes.add("WARN could not read battery voltage");
        }

        telemetry.addLine(passed ? "SYSTEMS CHECK: PASS" : "SYSTEMS CHECK: *** FAIL ***");
        for (String note : notes) telemetry.addLine(note);
        telemetry.addLine("Press START to pulse each motor, or STOP to exit.");
        telemetry.update();

        // Save the result before we even start (section 7).
        Persistence.Snapshot snap = new Persistence.Snapshot();
        snap.systemsCheckPassed = passed;
        snap.systemsCheckNotes = notes;
        snap.startingBatteryVolts = volts;
        Persistence.writeSnapshot(snap);

        waitForStart();
        if (!opModeIsActive()) return;

        // Active check: pulse each motor so a human confirms the RIGHT mechanism moves.
        for (String name : MOTOR_NAMES) {
            if (!opModeIsActive()) break;
            DcMotorEx motor;
            try {
                motor = hardwareMap.get(DcMotorEx.class, name);
            } catch (Throwable t) {
                continue;
            }
            telemetry.addLine("Pulsing '" + name + "' — watch that the correct mechanism moves.");
            telemetry.update();
            motor.setPower(0.25);
            sleep(400);
            motor.setPower(0.0);
            sleep(300);
        }

        telemetry.addLine("Systems check complete.");
        telemetry.update();
    }
}
