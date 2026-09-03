package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.Persistence;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Off-robot tests for rename-aware tuning lookup.
 *
 * WHY: renaming a tunable silently orphans every value already saved under the old name — the robot
 * reads nothing, falls back to the code default, and the next autosave overwrites the file with that
 * default. The tuned number is then gone with no error anywhere. These tests pin the fallback that
 * prevents it, using the real 2026-09-02 heading -> headingHold rename.
 */
public class PersistenceRenameTest {

    @Test
    public void currentNameIsPreferred() {
        Map<String, Object> values = new HashMap<>();
        values.put("Drivetrain.headingHoldP", 2.5);
        values.put("Drivetrain.headingP", 9.9);   // stale leftover
        assertEquals(2.5, Persistence.lookupWithRenames(values, "Drivetrain.headingHoldP"));
    }

    @Test
    public void formerNameIsFoundWhenCurrentIsAbsent() {
        Map<String, Object> values = new HashMap<>();
        values.put("Drivetrain.headingP", 1.2);
        assertEquals(1.2, Persistence.lookupWithRenames(values, "Drivetrain.headingHoldP"));
    }

    /** Every renamed key must resolve, not just the one that is easy to remember. */
    @Test
    public void allEightRenamedKeysResolve() {
        String[][] pairs = {
                {"Drivetrain.headingCorrectionEnabled",      "Drivetrain.headingHoldEnabled"},
                {"Drivetrain.headingCorrectionThresholdMin", "Drivetrain.headingHoldThresholdMin"},
                {"Drivetrain.headingCorrectionLagMs",        "Drivetrain.headingHoldLagMs"},
                {"Drivetrain.headingNominalVoltage",         "Drivetrain.headingHoldNominalVoltage"},
                {"Drivetrain.headingP", "Drivetrain.headingHoldP"},
                {"Drivetrain.headingI", "Drivetrain.headingHoldI"},
                {"Drivetrain.headingD", "Drivetrain.headingHoldD"},
                {"Drivetrain.headingF", "Drivetrain.headingHoldF"},
        };
        for (String[] pair : pairs) {
            Map<String, Object> values = new HashMap<>();
            values.put(pair[0], 7.0);
            assertEquals("former name " + pair[0] + " did not resolve",
                    7.0, Persistence.lookupWithRenames(values, pair[1]));
        }
    }

    @Test
    public void anUnknownKeyStillReturnsNull() {
        assertNull(Persistence.lookupWithRenames(new HashMap<String, Object>(), "Drivetrain.nope"));
    }

    /** Pedro keys share the word "heading" but live in their own namespace — never cross over. */
    @Test
    public void pedroHeadingKeysAreNotConfusedWithTheDrivetrainSet() {
        Map<String, Object> values = new HashMap<>();
        values.put("Pedro.headingP", 4.4);
        assertNull("a Pedro key must never satisfy a Drivetrain lookup",
                Persistence.lookupWithRenames(values, "Drivetrain.headingHoldP"));
    }
}
