package org.firstinspires.ftc.teamcode.logic;

/**
 * IntakeLogic — PURE LOGIC, no hardware (CLAUDE.md section 9). Unit-tested off-robot.
 *
 * This class touches no motors, no hardwareMap, no OpMode — it is plain math, so it can be tested
 * on a laptop with no robot (see IntakeLogicTest). The AI writes the function AND its tests, and
 * the logic is validated without ever powering on the robot.
 *
 * Deliberately simple: a single-motor intake has little math to isolate. The value here is the
 * PATTERN — this is where real mechanism math (a lift motion profile, a targeting solver) will
 * live and be tested when the game needs it. The Mode enum lives in this pure layer so both the
 * logic and the subsystem share it without a circular dependency.
 */
public final class IntakeLogic {

    public enum Mode { STOPPED, INTAKING, EJECTING }

    /**
     * Maps an intake mode to a signed motor power.
     *
     * @param mode        what the intake is trying to do
     * @param intakePower power to pull a piece IN  (0..1)
     * @param ejectPower  power to push a piece OUT (0..1); applied as negative
     * @return signed motor power, clamped to a safe range
     */
    public static double powerFor(Mode mode, double intakePower, double ejectPower) {
        switch (mode) {
            case INTAKING:
                return clamp01(intakePower);
            case EJECTING:
                return -clamp01(ejectPower);
            case STOPPED:
            default:
                return 0.0;
        }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private IntakeLogic() { }
}
