package org.firstinspires.ftc.teamcode.config;

import com.bylazar.configurables.annotations.Configurable;

/**
 * FieldTweaks — the (Field × Alliance) matrix of AutonFieldTweaks instances, exposed as live
 * configurables so tweaks can be edited from Panels without a code push (§6 Tier 1).
 *
 * PATTERN COPIED FROM: FTC 5327 SMS Robotics' decode-2025 (team/opmodes/auton/support/AutonBase.java
 * lines 22-28). They kept the six statics on their AutonBase; we split them into a dedicated
 * config class so `AutonomousExample` stays lean and Panels can find them via `@Configurable`.
 *
 * LOOKUP:
 *   FieldTweaks.lookup(alliance, field)  → returns the specific AutonFieldTweaks instance to apply
 *
 * If Panels can't recurse into the nested object fields, flatten to individual `public static
 * double xOffset_field1_red` etc. — we'll flip that at first competition if the nested form
 * doesn't render correctly.
 */
@Configurable
public class FieldTweaks {

    /** Which field we're currently running on. Selected via AutonMenu at init. */
    public enum Field {
        FIELD_1,
        FIELD_2,
        PRACTICE
    }

    public static AutonFieldTweaks field1Red = new AutonFieldTweaks();
    public static AutonFieldTweaks field1Blue = new AutonFieldTweaks();
    public static AutonFieldTweaks field2Red = new AutonFieldTweaks();
    public static AutonFieldTweaks field2Blue = new AutonFieldTweaks();
    public static AutonFieldTweaks practiceRed = new AutonFieldTweaks();
    public static AutonFieldTweaks practiceBlue = new AutonFieldTweaks();

    /**
     * @param isRed true for RED, false for BLUE. Kept as a boolean here (not the AutonMenu.Alliance
     *              enum) so this class has no dependency on the opmodes layer — respects §3.
     */
    public static AutonFieldTweaks lookup(boolean isRed, Field field) {
        switch (field) {
            case FIELD_1:  return isRed ? field1Red : field1Blue;
            case FIELD_2:  return isRed ? field2Red : field2Blue;
            case PRACTICE: return isRed ? practiceRed : practiceBlue;
            default:       return new AutonFieldTweaks(); // zeros — safe fallback
        }
    }

    private FieldTweaks() { } // static holder; never instantiated
}
