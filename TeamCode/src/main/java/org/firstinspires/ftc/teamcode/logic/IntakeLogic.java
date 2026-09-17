package org.firstinspires.ftc.teamcode.logic;

/**
 * IntakeLogic — pure math for turning a trigger squeeze into an intake motor power.
 *
 * WHY THIS IS SEPARATE FROM THE SUBSYSTEM: this is the whole decision the driver feels — "how hard
 * do I have to squeeze before the intake runs, and how fast does it then run" — and it is a
 * function of four numbers with no hardware in it. Kept here it is unit-tested off the robot (§9),
 * so the bench time goes into tuning the numbers, not into proving the arithmetic.
 *
 * No state, no allocation, no hardware. Safe to call every loop.
 */
public final class IntakeLogic {

    /**
     * Power for the intake motor given how far the trigger is squeezed.
     *
     * Hold-to-run: below the threshold the motor is off; at or past it the motor runs at the full
     * tuned power. The trigger is a switch with a dead band, not a speed dial — the driver gets the
     * same intake speed every time, which is what makes a tuned intakePower mean anything.
     *
     * The threshold exists because a trigger at rest does not read exactly 0.0, and a worn one can
     * rest well above it. Anything under the threshold must be treated as "not pressed" or the
     * intake runs on its own the moment the match starts.
     *
     * @param trigger   trigger value from the gamepad, 0..1
     * @param threshold squeeze past this to turn the intake on (typical: 0.25)
     * @param power     power to run at once the trigger is past the threshold, -1..1
     * @param maxPower  hard safety cap on magnitude, 0..1 (§5 actuator limits) — a mis-typed
     *                  power in the dashboard can never exceed this
     * @return motor power in [-maxPower, maxPower]; exactly 0 when the trigger is not pressed
     */
    public static double powerForTrigger(double trigger, double threshold,
                                         double power, double maxPower) {
        // NaN guard: a NaN trigger would fail every comparison and silently leave the intake off,
        // but a NaN power would be handed straight to the motor. Treat either as "off".
        if (Double.isNaN(trigger) || Double.isNaN(power)) return 0.0;
        if (trigger < threshold) return 0.0;
        return clamp(power, maxPower);
    }

    /**
     * Power for one side of a two-motor intake.
     *
     * A counter-rotating intake runs its two rollers toward each other, so one motor takes the
     * opposite sign. Zero stays exactly zero either way — a stopped motor has no direction, and
     * -0.0 would compare unequal to 0.0 in the subsystem's "skip repeat writes" check.
     *
     * @param power    the mechanism's power, -1..1
     * @param inverted true if this side runs opposite the other one
     * @return power, negated when inverted
     */
    public static double sidePower(double power, boolean inverted) {
        if (power == 0.0) return 0.0;
        return inverted ? -power : power;
    }

    /** Clamps value to [-limit, limit]. A negative limit is treated as 0 — no power at all. */
    public static double clamp(double value, double limit) {
        double safeLimit = Math.max(0.0, limit);
        if (value > safeLimit) return safeLimit;
        if (value < -safeLimit) return -safeLimit;
        return value;
    }

    private IntakeLogic() { } // static holder; never instantiated
}
