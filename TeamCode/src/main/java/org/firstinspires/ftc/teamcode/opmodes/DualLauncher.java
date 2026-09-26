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
 * DualLauncher — a bench test for a launcher driven by TWO motors, held at a chosen speed in RPM.
 * The single-motor version is {@link SingleLauncher}; the two keep separate tunables in Panels, so tuning
 * one never touches the other.
 *
 * THE MOTORS: two goBILDA Yellow Jacket 6000 RPM (1:1) motors, plugged into the ports the hub
 * configuration calls `LF_Motor` and `LR_Motor`. Those names are the front-left and left-rear DRIVE
 * motors in §10, so any drive OpMode (TeleOp, autos, SystemsCheck) spins the launcher too until it
 * has ports and names of its own. Change {@link #MOTOR_NAME} and {@link #SECOND_MOTOR_NAME} then.
 *
 * *** {@link #ticksPerRev} MUST MATCH THE MOTORS, or every RPM on this screen is wrong. *** A Yellow
 * Jacket counts 28 ticks per turn of the bare motor shaft, times the gearbox ratio. These are 1:1,
 * so it is 28. If a belt or gears sit between the motors and the wheel, multiply by that ratio too.
 *
 * WHAT IT DOES: nothing until you hold X. While X is held a feedforward-plus-PD controller drives
 * BOTH motors to {@link #targetRpm}. It measures speed from {@link #MOTOR_NAME} only and sends the
 * SAME power to both, so the two motors can never be asked to fight each other. Release X and both
 * go to zero — the wheel then coasts down, because a launcher wheel has real inertia and braking it
 * every shot is hard on the gearboxes. That is set once at init, not a knob.
 *
 * CHANGING THE SPEED: D-pad UP adds {@link #rpmStep} RPM, D-pad DOWN subtracts it, one step per
 * press. The target is clamped to {@link #minRpm}..{@link #maxRpm}. {@code targetRpm} is also a
 * Panels tunable, so you can type an exact number instead of stepping to it (§6 Tier 1) — and
 * because it is saved per robot, the speed you leave it at is the speed it starts at next time.
 *
 * HOW THE CONTROLLER WORKS — READ THIS BEFORE TUNING: power = {@code kF × targetRpm} (feedforward)
 * + {@code kP × error} − {@code kD × acceleration}. The PD part corrects the error. The feedforward
 * is a guess at the power that holds the wheel at the target speed, made before any error exists.
 * It is there because PD alone settles BELOW the target and stays there: {@code kP × error} would
 * be the only thing holding the wheel at speed, so the error could never reach zero. Set
 * {@link #kF} to 0 to see pure PD and that droop for yourself.
 *
 * TUNE IN THIS ORDER, one knob at a time (§6): set kP and kD to 0. Hold X and raise {@link #kF}
 * until the measured RPM lands on the target by itself. Check it at two or three targets — if it
 * is right at one speed and short at another, pick the value that is right where you shoot. Then
 * bring kP back to close the last gap and recover faster after a shot. Add kD only if it overshoots.
 * The right kF rises as the battery drops, so tune on a charged battery.
 *
 * WHAT TO WATCH: "RPM" against "Target RPM" is the whole test — how fast it gets there, how far
 * short it settles, and whether it recovers after a shot. Watch the two motor RPMs against EACH
 * OTHER too: same power and very different speeds means one motor is loaded, geared or wired
 * differently from the other. Both are free to read — encoder velocity rides the bulk read (§4).
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
@TeleOp(name = "Dual Launcher")
public class DualLauncher extends LinearOpMode {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------

    /**
     * Encoder ticks per ONE TURN of the launcher wheel. 28 is a goBILDA 6000 RPM (1:1) Yellow
     * Jacket driving the wheel directly. See the class comment before changing it.
     */
    public static double ticksPerRev = 28.0;

    /** Wheel speed the controller aims for, RPM. D-pad steps it; Panels can set it exactly. */
    public static double targetRpm = 3000.0;

    /** How much one D-pad press moves the target, RPM. */
    public static double rpmStep = 100.0;

    /**
     * Target is clamped to this range, so a stuck D-pad cannot run the target away. 6000 is the
     * motors' free speed; a loaded wheel tops out a little below it.
     */
    public static double minRpm = 0.0;
    public static double maxRpm = 6000.0;

    /**
     * Feedforward gain: motor power per RPM of TARGET. It supplies the power that holds the wheel
     * at speed, so PD only has to correct what is left over.
     *
     * HOW 0.00016 WAS CHOSEN: a bare 1:1 Yellow Jacket runs about 6000 RPM free at full power, so
     * 1 / 6000 ≈ 0.000167 power per RPM. A loaded wheel needs a little more, and a low battery
     * needs more again. This is a starting point, not a tuned value.
     */
    public static double kF = 0.00016;

    /**
     * Proportional gain: motor power per RPM of error, added on top of the feedforward.
     *
     * HOW 0.002 WAS CHOSEN: one D-pad step is 100 RPM, and 0.002 turns 100 RPM of error into 0.2
     * extra power — a firm push without slamming to full power on every step. From rest it still
     * asks for full power until the wheel is within about 500 RPM of the target, so spin-up is quick.
     *
     * TUNE IT: raise it to recover faster after a shot. Back off the moment the wheel starts
     * hunting — a rising and falling whine around the target means this gain is too high.
     */
    public static double kP = 0.002;

    /**
     * Derivative gain: motor power per (RPM per second) of wheel acceleration. It damps the
     * approach so the wheel does not sail past the target on spin-up. 0.00005 takes about 0.15
     * power off while the wheel is gaining 3000 RPM/s. Kept modest on purpose: this term acts on a
     * noisy encoder signal, and too much D makes the power jitter audibly. If it buzzes at a steady
     * speed, lower this first, before touching kP.
     */
    public static double kD = 0.00005;

    /**
     * Power ceiling, 0..1 — the actuator cap §5 asks for. Lower it to test a mechanism gently
     * without touching the gains.
     */
    public static double maxPower = 1.0;

    /**
     * The launcher motors' configuration names. Read once at init, so changing either needs a
     * restart of the OpMode, not just a dashboard edit.
     */
    private static final String MOTOR_NAME = "LF_Motor";
    private static final String SECOND_MOTOR_NAME = "LR_Motor";

    @Override
    public void runOpMode() {
        // MANUAL bulk caching, cleared once at the top of every loop (§4 rule 1).
        BulkReads bulkReads = new BulkReads(hardwareMap);

        DcMotorEx motor;
        DcMotorEx secondMotor;
        // Tracks which name we are about to look up, so a failure can name the motor that is
        // actually missing instead of making you guess between the two.
        String looking = MOTOR_NAME;
        try {
            motor = hardwareMap.get(DcMotorEx.class, MOTOR_NAME);
            looking = SECOND_MOTOR_NAME;
            secondMotor = hardwareMap.get(DcMotorEx.class, SECOND_MOTOR_NAME);
        } catch (Throwable t) {
            // Fail loud, and do not start (§5). Starting with only one of the two motors would be
            // worse than not starting: the dead motor would drag against the live one.
            telemetry.addLine("*** FAIL: motor '" + looking + "' is not in the configuration ***");
            telemetry.addLine("Fix the hub configuration, or change the motor names in DualLauncher.java.");
            telemetry.update();
            waitForStart();
            return;
        }

        // Coast down on release, set once, on both. A launcher wheel carries real inertia, and
        // braking dumps that energy into the gearboxes on every shot.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        secondMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        telemetry.addLine(RobotIdentity.resolve().banner());
        telemetry.addLine("Hold X to spin up. D-pad UP / DOWN changes the target speed.");
        telemetry.addData("ticksPerRev (6000 RPM motor = 28)", ticksPerRev);
        telemetry.update();

        waitForStart();

        // Looked up once, not per loop. Panels buffers lines until update() is called.
        TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();

        // What we last sent the motors. While X is held the controller asks for a slightly
        // different power every loop, so this mostly stops the repeated zero-writes while the
        // wheel is idle — a motor write is a round-trip to the hub (§0).
        double lastPower = Double.NaN;

        // Controller state. lastNanos == 0 marks the first pass, where there is no previous sample
        // to take a derivative against.
        long lastNanos = 0L;
        double lastRpm = 0.0;

        // D-pad edge detection: a button reads true for every loop it is held, so without this one
        // press would walk the target up by thousands of RPM.
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
            double ticks = Math.max(1.0, ticksPerRev);
            double rpm = motor.getVelocity() * 60.0 / ticks;
            double secondRpm = secondMotor.getVelocity() * 60.0 / ticks;

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
                // target sits still, but a D-pad press steps the target 100 RPM instantly — and
                // differentiating that step would kick the power for one loop for no physical
                // reason. Measuring the wheel instead means the target can jump freely.
                double rpmPerSecond = (dt > 0.0) ? (rpm - lastRpm) / dt : 0.0;
                double cap = Range.clip(maxPower, 0.0, 1.0);
                // Clamped at zero, never negative: this is a launcher, and driving the wheel
                // backwards to slow it down would be hard on the gearboxes for no gain (§5).
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
                secondMotor.setPower(power);
                lastPower = power;
            }

            // Driver Hub: target RPM, then one RPM line per motor, so a motor falling behind the
            // other is visible at a glance (§8 glanceable, §4 rule 6). The captions are constants
            // joined at compile time, so this builds no strings per loop (§4 rule 8).
            telemetry.addData("Target RPM", targetRpm);
            telemetry.addData("RPM " + MOTOR_NAME, rpm);
            telemetry.addData("RPM " + SECOND_MOTOR_NAME, secondRpm);
            telemetry.update();

            // Panels gets the rest, for tuning at the bench. Loop Hz lives here now: §4 rule 7
            // requires it be telemetered, and the D term depends on loop timing.
            panels.addData("Target RPM", targetRpm);
            panels.addData("RPM " + MOTOR_NAME, rpm);
            panels.addData("RPM " + SECOND_MOTOR_NAME, secondRpm);
            panels.addData("Error RPM", error);
            panels.addData("Power", power);
            panels.addData("X held", held);
            panels.addData("Loop Hz", loopHz);
            panels.update();
        }

        // Never leave a wheel spinning after the OpMode ends (§5). Both, every time.
        motor.setPower(0.0);
        secondMotor.setPower(0.0);
    }
}
