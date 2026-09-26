package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.configurables.annotations.Configurable;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * SingleLauncher — a bench test for a launcher wheel, held at a chosen speed in RPM.
 *
 * THE MOTOR: one goBILDA Yellow Jacket, 435 RPM (13.7:1), plugged into the port the hub
 * configuration calls `L_INTAKE` (Expansion Hub port 0). That name belongs to the INTAKE in §10,
 * so anything that runs the intake — the TeleOp right trigger, SystemsCheck — spins the launcher
 * too until it has a port and name of its own. Change {@link #MOTOR_NAME} when it does.
 *
 * EXPANSION HUB COST: every write to this motor crosses the RS485 link to the second hub, which
 * costs more loop time than a Control Hub port (§10). The loop already skips repeat writes; watch
 * Loop Hz on Panels.
 *
 * *** {@link #ticksPerRev} MUST MATCH THE MOTOR, or every RPM on this screen is wrong. *** Encoders
 * count ticks, not revolutions. A Yellow Jacket counts 28 ticks per turn of the bare motor shaft,
 * times the gearbox ratio: 28 × 13.7 ≈ 384.5 for this 435 RPM motor. If a belt or gears sit
 * between the motor and the wheel, multiply by that ratio too.
 *
 * WHAT IT DOES: nothing until you hold X. While X is held a feedforward-plus-PD controller drives
 * the motor to {@link #targetRpm}. Release X and the power goes to zero — the wheel then coasts
 * down, because a launcher wheel has real inertia and braking it every shot is hard on the gearbox.
 * That is set once at init, not a knob.
 *
 * CHANGING THE SPEED: D-pad UP adds {@link #rpmStep} RPM, D-pad DOWN subtracts it, one step per
 * press. The target is clamped to {@link #minRpm}..{@link #maxRpm}; the top of that range is the
 * motor's free speed, because asking for more than the motor can do just pins it at full power.
 * {@code targetRpm} is also a Panels tunable, so you can type an exact number instead of stepping
 * to it (§6 Tier 1) — and because it is saved per robot, the speed you leave it at is the speed it
 * starts at next time.
 *
 * HOW THE CONTROLLER WORKS — READ THIS BEFORE TUNING: power = {@code kF × targetRpm} (feedforward)
 * + {@code kP × error} − {@code kD × acceleration}. The feedforward does the bulk of the work. It is
 * a guess at the power that holds the wheel at the target speed, made before any error exists.
 * PD then only trims around that guess. Without feedforward, a PD loop settles BELOW the target
 * and stays there, because {@code kP × error} would be the only thing holding the wheel at speed —
 * so the error could never reach zero.
 *
 * TUNE IN THIS ORDER, one knob at a time (§6): set kP and kD to 0. Hold X and raise {@link #kF}
 * until the measured RPM lands on the target by itself. Check it at two or three targets — if it
 * is right at one speed and short at another, pick the value that is right where you shoot. Then
 * bring kP back to close the last gap and recover faster after a shot. Add kD only if it overshoots.
 * The right kF rises as the battery drops, so tune on a charged battery.
 *
 * WHAT TO WATCH: "RPM" against "Target RPM" is the whole test — how fast it gets there, how far
 * short it settles, and whether it recovers after a shot. Encoder velocity rides the bulk read, so
 * reading it costs nothing extra (§4).
 *
 * WHERE THE NUMBERS ARE: the Driver Hub shows Target RPM and RPM only. Error, power and Loop Hz
 * are on Panels, so have Panels open when you tune.
 *
 * LOOP-TIME READOUT (on Panels): required even though this never runs in a match. The D term divides by
 * the measured loop time, so timing is part of the control law — a loop that stutters makes the
 * derivative spike. Watch Loop Hz whenever you tune kD.
 *
 * WHY THIS IS A PLAIN LinearOpMode, not the command framework: it is a diagnostic, and a diagnostic
 * must still work when something above it is broken — the same reasoning as SystemsCheck. There is
 * one button and one control loop here; a scheduler and a subsystem would add layers without adding
 * safety. The real launcher subsystem, when the mechanism is designed, is a different job.
 */
@Configurable
@TeleOp(name = "Single Launcher")
public class SingleLauncher extends LinearOpMode {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------

    /**
     * Encoder ticks per ONE TURN of the launcher wheel. 384.5 is a goBILDA 435 RPM Yellow Jacket
     * (28 × 13.7) driving the wheel directly. See the class comment before changing it.
     */
    public static double ticksPerRev = 384.5;

    /** Wheel speed the controller aims for, RPM. D-pad steps it; Panels can set it exactly. */
    public static double targetRpm = 300.0;

    /** How much one D-pad press moves the target, RPM. */
    public static double rpmStep = 25.0;

    /**
     * Target is clamped to this range, so a stuck D-pad cannot run the target away. 435 is the
     * motor's free speed: the wheel cannot go faster, so a higher target would only pin it at full
     * power with the controller doing nothing useful.
     */
    public static double minRpm = 0.0;
    public static double maxRpm = 435.0;

    /**
     * Feedforward gain: motor power per RPM of TARGET. It supplies the power that holds the wheel
     * at speed, so PD only has to correct what is left over.
     *
     * HOW 0.0023 WAS CHOSEN: this motor runs about 435 RPM free at full power, so 1 / 435 ≈ 0.0023
     * power per RPM. A loaded wheel needs a little more, and a low battery needs more again. This
     * is a starting point, not a tuned value. Set it first, with kP and kD at zero — see the class
     * comment.
     */
    public static double kF = 0.0023;

    /**
     * Proportional gain: motor power per RPM of error, added on top of the feedforward.
     *
     * HOW 0.01 WAS CHOSEN: one D-pad step is 25 RPM, and 0.01 turns 25 RPM of error into 0.25
     * extra power — a firm push without slamming to full power on every step. The whole speed
     * range is only 435 RPM, so this is much larger than a gain for a 6000 RPM motor would be.
     *
     * TUNE IT: raise it to recover faster after a shot. Back off the moment the wheel starts
     * hunting — a rising and falling whine around the target means this gain is too high.
     */
    public static double kP = 0.01;

    /**
     * Derivative gain: motor power per (RPM per second) of wheel acceleration. It damps the
     * approach so the wheel does not sail past the target on spin-up.
     *
     * Starts at 0 on purpose: feedforward plus P rarely overshoots on a flywheel, and this term
     * acts on a noisy encoder signal, so it earns its place only if you see overshoot. If you add
     * it, start near 0.0005 and lower it first if the power starts to buzz at a steady speed.
     */
    public static double kD = 0.0;

    /**
     * Power ceiling, 0..1 — the actuator cap §5 asks for. Lower it to test a mechanism gently
     * without touching the gains.
     */
    public static double maxPower = 1.0;

    /**
     * The launcher motor's configuration name. Read once at init, so changing it needs a restart
     * of the OpMode, not just a dashboard edit.
     */
    private static final String MOTOR_NAME = "L_INTAKE";

    @Override
    public void runOpMode() {
        // MANUAL bulk caching, cleared once at the top of every loop (§4 rule 1).
        BulkReads bulkReads = new BulkReads(hardwareMap);

        DcMotorEx motor;
        try {
            motor = hardwareMap.get(DcMotorEx.class, MOTOR_NAME);
        } catch (Throwable t) {
            // Fail loud, and do not start (§5). A bench test that silently does nothing wastes a
            // bench session and teaches you the wrong thing about the mechanism.
            telemetry.addLine("*** FAIL: motor '" + MOTOR_NAME + "' is not in the configuration ***");
            telemetry.addLine("Fix the hub configuration, or change MOTOR_NAME in SingleLauncher.java.");
            telemetry.update();
            waitForStart();
            return;
        }

        // Coast down on release, set once. A launcher wheel carries real inertia, and braking dumps
        // that energy into the gearbox on every shot. Change this line to BRAKE if the mechanism
        // ever needs a fast stop.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        telemetry.addLine(RobotIdentity.resolve().banner());
        telemetry.addLine("Hold X to spin up. D-pad UP / DOWN changes the target speed.");
        telemetry.addData("ticksPerRev (435 RPM motor = 384.5)", ticksPerRev);
        telemetry.update();

        waitForStart();

        // Looked up once, not per loop. Panels buffers lines until update() is called.
        TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();

        // What we last sent the motor. While X is held the controller asks for a slightly different
        // power every loop, so this mostly stops the repeated zero-writes while the wheel is idle —
        // a motor write is a round-trip to the hub (§0).
        double lastPower = Double.NaN;

        // Controller state. lastNanos == 0 marks the first pass, where there is no previous sample
        // to take a derivative against.
        long lastNanos = 0L;
        double lastRpm = 0.0;

        // D-pad edge detection: a button reads true for every loop it is held, so without this one
        // press would walk the target up by hundreds of RPM.
        boolean lastDpadUp = false;
        boolean lastDpadDown = false;

        // Loop timing, for Loop Hz and for the derivative.
        long loopStartNanos = System.nanoTime();
        double loopHz = 0.0;

        while (opModeIsActive()) {
            // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (§4).
            bulkReads.clear();

            long now = System.nanoTime();
            double loopSeconds = (now - loopStartNanos) / 1_000_000_000.0;
            if (loopSeconds > 0) loopHz = 1.0 / loopSeconds;
            loopStartNanos = now;

            // ---- READ (§4 rule 2): one pass, one snapshot, no encoder read twice --------------
            boolean held = gamepad1.x;
            boolean dpadUp = gamepad1.dpad_up;
            boolean dpadDown = gamepad1.dpad_down;

            // Guard the divide: ticksPerRev is a live tunable, and a zero typed into Panels would
            // turn every RPM into infinity and the controller output into nonsense.
            double rpm = motor.getVelocity() * 60.0 / Math.max(1.0, ticksPerRev);

            // ---- PROCESS ---------------------------------------------------------------------
            // Step the target on the PRESS, not while held.
            if (dpadUp && !lastDpadUp) targetRpm += rpmStep;
            if (dpadDown && !lastDpadDown) targetRpm -= rpmStep;
            lastDpadUp = dpadUp;
            lastDpadDown = dpadDown;
            targetRpm = Range.clip(targetRpm, minRpm, maxRpm);

            double error = targetRpm - rpm;
            double power = 0.0;

            if (held) {
                double dt = (lastNanos == 0L) ? 0.0 : (now - lastNanos) / 1_000_000_000.0;
                // Derivative of the MEASUREMENT, not of the error. They are the same while the
                // target sits still, but a D-pad press steps the target instantly — and
                // differentiating that step would kick the power for one loop for no physical
                // reason. Measuring the wheel instead means the target can jump freely.
                double rpmPerSecond = (dt > 0.0) ? (rpm - lastRpm) / dt : 0.0;
                double cap = Range.clip(maxPower, 0.0, 1.0);
                // Clamped at zero, never negative: this is a launcher, and driving the wheel
                // backwards to slow it down would be hard on the gearbox for no gain (§5).
                power = Range.clip(kF * targetRpm + kP * error - kD * rpmPerSecond, 0.0, cap);
            } else {
                // Not spinning: forget the history so that re-gripping X does not see a huge
                // apparent acceleration across the gap and spike the derivative.
                lastNanos = 0L;
            }

            if (held) lastNanos = now;
            lastRpm = rpm;

            // ---- WRITE -----------------------------------------------------------------------
            if (power != lastPower) {
                motor.setPower(power);
                lastPower = power;
            }

            // Driver Hub: target and actual RPM, nothing else — the D-pad moves the first, the
            // wheel follows with the second (§8 glanceable, §4 rule 6). Numbers, not built strings
            // (§4 rule 8).
            telemetry.addData("Target RPM", targetRpm);
            telemetry.addData("RPM", rpm);
            telemetry.update();

            // Panels gets the rest, for tuning at the bench. Loop Hz lives here now: §4 rule 7
            // requires it be telemetered, and the D term depends on loop timing.
            panels.addData("Target RPM", targetRpm);
            panels.addData("RPM", rpm);
            panels.addData("Error RPM", error);
            panels.addData("Power", power);
            panels.addData("X held", held);
            panels.addData("Loop Hz", loopHz);
            panels.update();
        }

        // Never leave a wheel spinning after the OpMode ends (§5).
        motor.setPower(0.0);
    }
}
