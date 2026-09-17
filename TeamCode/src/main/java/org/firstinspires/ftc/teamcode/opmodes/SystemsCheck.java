package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.util.LogCleanup;
import org.firstinspires.ftc.teamcode.util.Persistence;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

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

    // Names must match the Robot Controller configuration exactly (CLAUDE.md §10).
    // Add game-mechanism motors here when they are wired and configured.
    private static final String[] MOTOR_NAMES = {
            "LF_Motor", "LR_Motor", "RF_Motor", "RR_Motor",
            // Game mechanisms. The pulse below spins each one briefly — watch that the INTAKE moves
            // and not something else, which is how a swapped port shows up on the bench.
            Intake.MOTOR_NAME
    };

    @Override
    public void runOpMode() {
        // Which robot is this? Resolve FIRST — tuning load is per-robot (see RobotIdentity).
        RobotIdentity robotId = RobotIdentity.resolve();
        Persistence.loadAndApplyTuning(robotId, telemetry);
        LogCleanup.maybeRun(telemetry); // fires once every 14 days, silent otherwise

        List<String> notes = new ArrayList<>();
        boolean passed = true;

        // --- which robot is this? (from the hub network name) ---
        notes.add(robotId.banner());
        if (!robotId.isKnown()) {
            // Not a hard fail — SystemsCheck runs on both robots, and an unnamed bench hub is a
            // legitimate state. But it means tuning selection failed closed (loaded nothing), so
            // say so loudly here rather than let it surprise anyone later.
            notes.add("WARN robot identity UNKNOWN — set the hub name to 34672-RC or 34672-T-RC");
        }

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

        // ─────────────────────────────────────────────────────────────────────────────────────
        // TODO: SystemsCheck build-out — planned checks, not yet implemented.
        //
        //  (a) Status indicator, driven from the pass/warn/fail state (CLAUDE.md §5 "deterministic
        //      init, fail loud").
        //
        //      THE SYSTEMS-CHECK STATUS + WARNINGS LIVE ON THE GAMEPAD LEDs (driver-facing, in-hand) —
        //      NOT the Control Hub. The pre-play warnings belong in the drivers' hands, not on a light
        //      on the robot they aren't looking at. Team runs Sony DualShock 4 / DualSense, which have
        //      a controllable RGB light bar (an F310 would NOT — no addressable LED). Scheme:
        //          gamepad1 steady BLUE = driver pad, all checks good
        //          gamepad2 steady RED  = operator pad, all checks good
        //              (blue vs red ALSO solves "which controller is which" — a bonus, not just status)
        //          FLASH YELLOW on a pad when a check tied to it FAILS/WARNS — e.g. that pad's stick is
        //          not within the zero point (|axis| >= JoystickCurve.deadzone, 0.05 — this is check (b)).
        //      API (r,g,b in 0..1):
        //          steady:   gamepad1.setLedColor(0,0,1, Gamepad.LED_DURATION_CONTINUOUS); // blue
        //                    gamepad2.setLedColor(1,0,0, Gamepad.LED_DURATION_CONTINUOUS); // red
        //          flashing: gamepad1.runLedEffect(new Gamepad.LedEffect.Builder()
        //                        .addStep(1,1,0,250)   // yellow 250ms
        //                        .addStep(0,0,0,250)   // off    250ms
        //                        .setRepeating(true).build());
        //
        //      ROBOT-MOUNTED LEDs — FUTURE, SEPARATE PURPOSE (not the gamepad pre-play status). Planned
        //      add-on light(s) for VARIOUS OTHER checks — e.g. subsystem health, game-piece count,
        //      alliance/mode. The Control Hub's own onboard LED can be folded into this robot-side
        //      scheme (it's a LynxModule implementing Blinker: hub.setConstant / hub.setPattern, via
        //      hardwareMap.getAll(LynxModule.class) — same handle BulkReads grabs), as can a REV
        //      Blinkin + LED strip (runs as a servo — see the SampleRevBlinkinLedDriver sample) for an
        //      across-the-field indicator. Define exactly which checks map to which colors when the
        //      robot LED hardware is chosen.
        //
        //      Wrap it all behind a util/StatusLED helper. LOOP-COST NOTE (§0/§4): set every LED /
        //      pattern / effect only on state CHANGE — the gamepad effect and any hub/strip pattern
        //      animate themselves (DS-side / firmware), so flashing costs nothing per loop; re-sending
        //      each loop wastes bandwidth AND restarts the animation at step 0 so it never blinks.
        //      NOTE: the Driver Hub's open USB port canNOT drive an LED (no SDK path).
        //
        //  (b) Stick-at-rest / drift check. Before START, confirm every gamepad axis reads within
        //      the deadzone — i.e. |axis| < JoystickCurve.deadzone (the "zero point", currently
        //      0.05). A stick that rests outside the deadzone will command drive the instant the
        //      match starts. Check driver + operator left/right X/Y (and triggers) and FAIL loud
        //      if any is drifting, naming the offending stick.
        //
        //  (c) Sensor checks — Limelight reachable, Pinpoint (I2C) responding, rangefinder reading.
        // ─────────────────────────────────────────────────────────────────────────────────────

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
        snap.robot = robotId.robot.name();
        snap.networkName = robotId.networkName;
        Persistence.writeSnapshot(snap, hardwareMap);

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
        Persistence.saveTuning(robotId);
    }
}
