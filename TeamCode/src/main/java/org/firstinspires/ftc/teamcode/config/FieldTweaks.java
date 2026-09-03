package org.firstinspires.ftc.teamcode.config;

import com.bylazar.configurables.annotations.Configurable;

/**
 * FieldTweaks — per-field, per-alliance pose deltas, as FLAT tunables so they persist and autosave.
 *
 * WHY: two competition fields are never exactly the same. Small differences in tape lines or wall
 * squareness make an autonomous that works on Field 1 drift a few inches on Field 2. Rather than
 * retuning every path, apply one offset for that field/alliance pair, tuned live in Panels between
 * matches. See {@link AutonFieldTweaks} for the value each offset carries.
 *
 * WHY FLAT AND NOT SIX NESTED OBJECTS (changed 2026-09-02): these numbers are MEASURED ON A
 * PHYSICAL FIELD, so losing them is expensive — you cannot re-derive them at a desk. They used to
 * live as six {@code AutonFieldTweaks} objects, and an object field is something the tuning system
 * cannot cheaply protect:
 *
 *   - {@code TunableWatcher} compares every tunable each loop to notice an edit and save it. It
 *     reads a double with {@code Field.getDouble}, which costs one primitive read and allocates
 *     nothing. There is no equivalent for an object — it would have to reach inside and diff each
 *     value every loop, which costs time and creates garbage, so the watcher skips object fields.
 *     Nested, these tweaks only ever saved on a clean OpMode stop; a crash or a flat battery lost
 *     them.
 *   - Persistence restores a double straight from the file. A nested object has to be rebuilt by
 *     GSON, which works but is the fiddliest path in the restore code.
 *
 * Flattening turns one thing the tooling cannot watch into eighteen things it can. The cost is
 * verbosity here and a {@link #lookup} that builds its result instead of returning a stored object.
 *
 * HOW TO USE — unchanged; {@code lookup} still hands back one {@link AutonFieldTweaks}:
 *   AutonFieldTweaks tweaks = FieldTweaks.lookup(menu.isRed(), menu.getField());
 */
@Configurable
public class FieldTweaks {

    /** Which field we're currently running on. Selected via AutonMenu at init. */
    public enum Field {
        FIELD_1,
        FIELD_2,
        PRACTICE
    }

    // Eighteen numbers: three offsets × three fields × two alliances. X and Y are inches, heading is
    // degrees. Named <field><Alliance>_<offset> so Panels lists each field's pair together.

    public static double field1Red_xOffsetInches = 0.0;
    public static double field1Red_yOffsetInches = 0.0;
    public static double field1Red_headingOffsetDeg = 0.0;

    public static double field1Blue_xOffsetInches = 0.0;
    public static double field1Blue_yOffsetInches = 0.0;
    public static double field1Blue_headingOffsetDeg = 0.0;

    public static double field2Red_xOffsetInches = 0.0;
    public static double field2Red_yOffsetInches = 0.0;
    public static double field2Red_headingOffsetDeg = 0.0;

    public static double field2Blue_xOffsetInches = 0.0;
    public static double field2Blue_yOffsetInches = 0.0;
    public static double field2Blue_headingOffsetDeg = 0.0;

    public static double practiceRed_xOffsetInches = 0.0;
    public static double practiceRed_yOffsetInches = 0.0;
    public static double practiceRed_headingOffsetDeg = 0.0;

    public static double practiceBlue_xOffsetInches = 0.0;
    public static double practiceBlue_yOffsetInches = 0.0;
    public static double practiceBlue_headingOffsetDeg = 0.0;

    /**
     * Collects the three offsets for one field/alliance pair.
     *
     * The returned object is BUILT from the statics above, not stored, so the flat fields stay the
     * single source of truth — there is no second copy to drift out of sync. It allocates a small
     * object per call, which is why this is an INIT-TIME call (AutonMenu.resolve) and must not move
     * into the loop (CLAUDE.md §4 rule 8).
     *
     * @param isRed true for RED, false for BLUE. Kept as a boolean rather than the AutonMenu.Alliance
     *              enum so this class has no dependency on the opmodes layer — respects §3.
     */
    public static AutonFieldTweaks lookup(boolean isRed, Field field) {
        AutonFieldTweaks t = new AutonFieldTweaks();
        switch (field) {
            case FIELD_1:
                t.xOffsetInches      = isRed ? field1Red_xOffsetInches      : field1Blue_xOffsetInches;
                t.yOffsetInches      = isRed ? field1Red_yOffsetInches      : field1Blue_yOffsetInches;
                t.headingOffsetDeg   = isRed ? field1Red_headingOffsetDeg   : field1Blue_headingOffsetDeg;
                break;
            case FIELD_2:
                t.xOffsetInches      = isRed ? field2Red_xOffsetInches      : field2Blue_xOffsetInches;
                t.yOffsetInches      = isRed ? field2Red_yOffsetInches      : field2Blue_yOffsetInches;
                t.headingOffsetDeg   = isRed ? field2Red_headingOffsetDeg   : field2Blue_headingOffsetDeg;
                break;
            case PRACTICE:
                t.xOffsetInches      = isRed ? practiceRed_xOffsetInches    : practiceBlue_xOffsetInches;
                t.yOffsetInches      = isRed ? practiceRed_yOffsetInches    : practiceBlue_yOffsetInches;
                t.headingOffsetDeg   = isRed ? practiceRed_headingOffsetDeg : practiceBlue_headingOffsetDeg;
                break;
            default:
                break;   // zeros — safe fallback, same as before
        }
        return t;
    }

    private FieldTweaks() { } // static holder; never instantiated
}
