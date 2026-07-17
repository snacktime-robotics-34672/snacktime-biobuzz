package org.firstinspires.ftc.teamcode.util;

/**
 * JoystickCurve — pure math for shaping raw stick input into driver-friendly output.
 *
 * WHY: Raw linear input (out = in) feels twitchy at low speeds and mushy at high speeds. This
 * shapes the response as:
 *   1. Deadzone — anything below deadzone is exactly zero (no idle drift).
 *   2. Linear ramp — from just past deadzone up to a "transition point," output is a straight
 *      line from minOutput to transitionOutput. This region is where fine driving happens; a
 *      shallow slope here means precise low-speed control.
 *   3. Exponential ramp — beyond the transition point, output curves upward to 1.0 with a
 *      derived exponent so full stick still reaches full power without a discontinuity.
 *
 * WHY NOT: If drivers prefer the raw feel, don't use it. This is a driver-experience choice, not
 * a correctness one. Test both back-to-back and let the drive team decide.
 *
 * Extracted from FTC 5327 SMS Robotics' decode-2025 (common/hardware/SalineGamepad.java lines
 * 135-159). The pure static function is the whole extraction — we don't need their gamepad wrapper
 * because SolversLib's GamepadEx already gives us button bindings.
 */
public class JoystickCurve {

    /**
     * Applies the deadzone + linear-then-exponential curve to a raw stick value.
     *
     * @param input             raw stick value in [-1, 1]
     * @param deadzone          input magnitude below this returns 0 (typical: 0.05)
     * @param minOutput         output value immediately past the deadzone (typical: 0.0)
     * @param transitionPoint   fraction of post-deadzone range where curve switches from linear to
     *                          exponential (typical: 0.5)
     * @param transitionOutput  output value at the transition point (typical: 0.35 — a shallow
     *                          slope in the linear region gives precise low-speed control)
     * @return shaped output in [-1, 1], sign preserved from input
     */
    public static double apply(double input, double deadzone, double minOutput,
                               double transitionPoint, double transitionOutput) {
        if (Math.abs(input) < deadzone) {
            return 0;
        }

        // Rescale |input| to [0, 1] after removing the deadzone
        double scaledInput = (Math.abs(input) - deadzone) / (1 - deadzone);

        double output;
        if (scaledInput <= transitionPoint) {
            // Linear ramp from minOutput up to transitionOutput
            output = minOutput + (transitionOutput - minOutput) * (scaledInput / transitionPoint);
        } else {
            // Auto-derived exponent keeps the exponential portion smooth up to 1.0 at full stick.
            // 0.8 is empirically tuned by decode-2025's authors; adjust here if drivers want a
            // steeper/shallower high-end response.
            double derivedExponent = 0.8 / (1 - transitionOutput);
            double adjustedInput = (scaledInput - transitionPoint) / (1 - transitionPoint);
            output = transitionOutput + (1 - transitionOutput) * Math.pow(adjustedInput, derivedExponent);
        }

        return Math.signum(input) * output;
    }

    private JoystickCurve() { } // static holder; never instantiated
}