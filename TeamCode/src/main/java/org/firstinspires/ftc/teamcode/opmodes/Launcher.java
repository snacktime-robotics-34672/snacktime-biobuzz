package org.firstinspires.ftc.teamcode.opmodes;

import com.bylazar.configurables.annotations.Configurable;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.teamcode.util.BulkReads;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * Launcher — a bench test for a launcher wheel. Hold X to spin, release to stop.
 *
 * *** THE ROBOT MUST BE ON BLOCKS. *** This spins `LF_Motor` AND `LR_Motor`, which are the
 * FRONT-LEFT and LEFT-REAR DRIVE motors in the hub configuration (§10). Both are on the same side
 * of the robot, so on the ground they do not just move it — they drive it hard to one side. Change
 * {@link #MOTOR_NAME} and {@link #SECOND_MOTOR_NAME} once the launcher has ports of its own.
 *
 * WHAT IT DOES: nothing until you hold X. While X is held BOTH motors run at {@code launcherPower}
 * (0.9 to start), from the same number, so they can never be asked for different powers. Release X
 * and both go to zero — the wheels then coast down, because a launcher wheel has real inertia and
 * braking it every shot is hard on the gearbox. That is set once at init, not a knob.
 *
 * WHAT TO WATCH: the two "Velocity" lines are the numbers that matter for a launcher. They tell you
 * how long each wheel takes to spin up and whether it recovers between shots. Watch them against
 * EACH OTHER too: same power and very different velocities means one motor is loaded, geared or
 * wired differently from the other. Both are free to read — encoder velocity rides the bulk read
 * (§4). The Driver Hub shows only those two, the power, and whether X is held.
 *
 * NO LOOP-TIME READOUT — a DELIBERATE exception to §4 rule 7, decided by Aaron on 2026-09-21, and
 * scoped to this OpMode alone. Every other OpMode still measures and telemeters ms + Hz. The
 * reasoning: this is a bench rig for one wheel, not a match path. It drives one motor from one
 * button and never runs in a match, so there is no control loop here whose timing could regress
 * into a driving fault. Still true with two motors: one button, one power, no control loop. If this
 * ever grows into a real Launcher subsystem, the readout comes back with it.
 *
 * TUNE IT LIVE: {@code launcherPower} is the one knob, a Panels configurable (§6 Tier 1), so you
 * can try 0.85 or 0.95 while the OpMode is running, with no deploy at all. It is saved per robot on
 * stop.
 *
 * WHY THIS IS A PLAIN LinearOpMode, not the command framework: it is a diagnostic, and a diagnostic
 * must still work when something above it is broken — the same reasoning as SystemsCheck. There is
 * one button and one motor here; a scheduler and a subsystem would add layers without adding
 * safety. The real Launcher subsystem, when the mechanism is designed, is a different job.
 */
@Configurable
@TeleOp(name = "Launcher")
public class Launcher extends LinearOpMode {

    // ---- Tunables (Panels live-editable, §6 Tier 1) ----------------------------------------

    /**
     * Power while X is held, -1..1. Start at 0.9 and adjust in Panels while the wheel is spinning.
     * Clamped to a legal power before it reaches the motor, so a typo cannot do anything worse than
     * full speed.
     */
    public static double launcherPower = 0.9;

    /**
     * The motors to spin. FRONT-LEFT and LEFT-REAR DRIVE MOTORS for now — see the class comment and
     * put the robot on blocks. Read once at init, so changing either needs a restart of the OpMode,
     * not just a dashboard edit.
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
            // Fail loud, and do not start (§5). A bench test that silently does nothing wastes a
            // bench session and teaches you the wrong thing about the mechanism. Starting with only
            // one of the two motors would be worse than not starting: the wheels would fight.
            telemetry.addLine("*** FAIL: motor '" + looking + "' is not in the configuration ***");
            telemetry.addLine("Fix the hub configuration, or change the motor names in Launcher.java.");
            telemetry.update();
            waitForStart();
            return;
        }

        // Coast down on release, set once, on both. A launcher wheel carries real inertia, and
        // braking dumps that energy into the gearbox on every shot. Change these lines to BRAKE if
        // the mechanism ever needs a fast stop.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        secondMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        telemetry.addLine(RobotIdentity.resolve().banner());
        telemetry.addLine("*** PUT THE ROBOT ON BLOCKS — this spins TWO DRIVE motors ***");
        telemetry.addLine("Hold X to spin the launcher. Release to stop.");
        telemetry.update();

        waitForStart();

        // What we last sent the motors. A motor write is a round-trip to the hub, and this OpMode
        // sits at one of two values almost all the time, so only write when it actually changes
        // (§0 — the loop budget is dominated by I/O, not math). One field covers both motors
        // because they are always given the same number; that is what keeps them in step.
        double lastPower = Double.NaN;

        while (opModeIsActive()) {
            // RULE 1, NON-NEGOTIABLE: clear the bulk cache FIRST, every loop, always (§4).
            bulkReads.clear();

            // Read -> process -> write (§4 rule 2).
            boolean held = gamepad1.x;
            double power = held ? Range.clip(launcherPower, -1.0, 1.0) : 0.0;

            if (power != lastPower) {
                motor.setPower(power);
                secondMotor.setPower(power);
                lastPower = power;
            }

            // The Driver Hub shows the launcher and NOTHING else. You are watching a wheel spin up
            // and reading one number off the screen while you do it; anything else is clutter (§8
            // asks for glanceable, §4 rule 6 for a minimal Driver Hub set).
            telemetry.addData("X held", held ? "SPINNING" : "off");
            telemetry.addData("Power", power);
            // One line per motor, ticks/sec, both riding the bulk read. Two lines instead of one is
            // the point now: you are checking the wheels match, not just that they turn.
            telemetry.addData("Velocity " + MOTOR_NAME, motor.getVelocity());
            telemetry.addData("Velocity " + SECOND_MOTOR_NAME, secondMotor.getVelocity());
            telemetry.update();

        }

        // Never leave a wheel spinning after the OpMode ends (§5). Both, every time.
        motor.setPower(0.0);
        secondMotor.setPower(0.0);
    }
}
