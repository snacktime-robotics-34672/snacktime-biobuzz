package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.pedroPathing.PedroTuningStore;
import org.firstinspires.ftc.teamcode.pedroPathing.TuningRecorder;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot unit tests for the pure half of TuningRecorder — the change detector and the
 * paste-ready snippet it prints. Run with ./gradlew :TeamCode:test.
 *
 * WHY THESE MATTER: the snippet is what a student pastes into Constants.java to save a tuning
 * session, sometimes at a competition. If it names the wrong constant set, or a builder method that
 * does not exist, the paste fails exactly when there is no time to debug it. These tests pin the
 * field prefixes and the shape of the generated code.
 */
public class TuningRecorderTest {

    /** Values 0..20 in tracked order, so each index shows up in the snippet as its own number. */
    private static double[] rampValues() {
        double[] v = new double[PedroTuningStore.VALUE_COUNT];
        for (int i = 0; i < v.length; i++) v[i] = i;
        return v;
    }

    // ---- fieldPrefixFor: the right robot's constant set --------------------------

    @Test
    public void competitionValuesGoInTheCompSet() {
        assertEquals("comp", TuningRecorder.fieldPrefixFor(RobotIdentity.Robot.COMPETITION));
    }

    @Test
    public void testbotValuesGoInTheTestSet() {
        assertEquals("test", TuningRecorder.fieldPrefixFor(RobotIdentity.Robot.TESTBOT));
    }

    /** An unidentified hub must never be told to paste its numbers into comp's set. */
    @Test
    public void unknownValuesGoInTheFallbackSet() {
        assertEquals("fallback", TuningRecorder.fieldPrefixFor(RobotIdentity.Robot.UNKNOWN));
    }

    // ---- changed: the per-loop detector ------------------------------------------

    @Test
    public void identicalValuesAreNotAChange() {
        assertFalse(TuningRecorder.changed(rampValues(), rampValues()));
    }

    @Test
    public void anyMovedValueIsAChange() {
        for (int i = 0; i < PedroTuningStore.VALUE_COUNT; i++) {
            double[] moved = rampValues();
            moved[i] += 0.001;
            assertTrue("index " + i + " should register as a change",
                    TuningRecorder.changed(moved, rampValues()));
        }
    }

    // ---- format: the snippet a student pastes ------------------------------------

    @Test
    public void snippetTargetsTheRobotsOwnFields() {
        String out = TuningRecorder.format(RobotIdentity.Robot.TESTBOT, rampValues());
        assertTrue(out.contains("public static FollowerConstants testFollowerConstants"));
        assertTrue(out.contains("public static MecanumConstants testMecanumConstants"));
        assertTrue(out.contains("public static PinpointConstants testPinpointConstants"));
        assertFalse("must not write into the other robot's set", out.contains("compFollower"));
    }

    /** Locks the tracked-value order: each gain must land in the argument that owns it. */
    @Test
    public void snippetPlacesEachValueInTheRightSlot() {
        String out = TuningRecorder.format(RobotIdentity.Robot.TESTBOT, rampValues());
        assertTrue(out.contains(".translationalPIDFCoefficients(new PIDFCoefficients(0.0, 1.0, 2.0, 3.0))"));
        assertTrue(out.contains(".headingPIDFCoefficients(new PIDFCoefficients(4.0, 5.0, 6.0, 7.0))"));
        assertTrue(out.contains(".drivePIDFCoefficients(new FilteredPIDFCoefficients(8.0, 9.0, 10.0, 11.0, 12.0))"));
        assertTrue(out.contains(".centripetalScaling(13.0)"));
        assertTrue(out.contains(".mass(14.0)"));
        assertTrue(out.contains(".forwardZeroPowerAcceleration(15.0)"));
        assertTrue(out.contains(".lateralZeroPowerAcceleration(16.0)"));
        assertTrue(out.contains("mecanumFor(17.0, 18.0, 1.0)"));
        assertTrue(out.contains("pinpointFor(19.0, 20.0)"));
    }
}
