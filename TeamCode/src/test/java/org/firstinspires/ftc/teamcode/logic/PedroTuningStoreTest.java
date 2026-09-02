package org.firstinspires.ftc.teamcode.logic;

import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.ftc.drivetrains.MecanumConstants;
import com.pedropathing.ftc.localization.constants.PinpointConstants;

import org.firstinspires.ftc.teamcode.pedroPathing.PedroTuningStore;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot unit tests for the Pedro tuning table — the save/load path for the values that decide
 * how each robot follows a path. Run with ./gradlew :TeamCode:test.
 *
 * THE TEST THAT MATTERS MOST is the round trip. capture() and restore() are hand-written inverses
 * indexed by position, so a value added to one and not the other, or two indices swapped, would
 * save perfectly and restore into the wrong gain — silently, with the file and the dashboard both
 * looking right. Round-tripping every value through a distinct number catches exactly that.
 */
public class PedroTuningStoreTest {

    /** Distinct, plausible values so a swapped index cannot coincidentally pass. */
    private static double[] distinctValues() {
        double[] v = new double[PedroTuningStore.VALUE_COUNT];
        for (int i = 0; i < v.length; i++) v[i] = (i + 1) * 0.37;
        v[14] = 6.5;      // mass must be > 0
        v[17] = 78.27;    // velocities must be > 0
        v[18] = 61.58;
        return v;
    }

    private static double[] captureFresh(FollowerConstants f, MecanumConstants m, PinpointConstants p) {
        double[] out = new double[PedroTuningStore.VALUE_COUNT];
        PedroTuningStore.capture(out, f, m, p);
        return out;
    }

    // ---- the round trip ----------------------------------------------------------

    /** Every value must survive restore() -> capture() unchanged, in its own slot. */
    @Test
    public void everyValueRoundTripsThroughTheLiveObjects() {
        FollowerConstants f = new FollowerConstants();
        MecanumConstants m = new MecanumConstants();
        PinpointConstants p = new PinpointConstants();

        double[] written = distinctValues();
        PedroTuningStore.restore(written, f, m, p);

        assertArrayEquals("a value landed in the wrong slot, or is missing from capture/restore",
                written, captureFresh(f, m, p), 0.0);
    }

    /** The filtered drive PIDF takes (P,I,D,T,F) while its fields read P,I,D,F,T — pin the order. */
    @Test
    public void driveFilteredCoefficientsKeepTAndFDistinct() {
        FollowerConstants f = new FollowerConstants();
        MecanumConstants m = new MecanumConstants();
        PinpointConstants p = new PinpointConstants();

        double[] v = distinctValues();
        v[11] = 0.61;   // T
        v[12] = 0.049;  // F
        PedroTuningStore.restore(v, f, m, p);

        assertEquals(0.61, f.coefficientsDrivePIDF.T, 0.0);
        assertEquals(0.049, f.coefficientsDrivePIDF.F, 0.0);
    }

    // ---- the map layer -----------------------------------------------------------

    @Test
    public void keysAreNamespacedAndComplete() {
        assertEquals(PedroTuningStore.VALUE_COUNT, PedroTuningStore.KEYS.length);
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < PedroTuningStore.VALUE_COUNT; i++) {
            map.put(PedroTuningStore.PREFIX + PedroTuningStore.KEYS[i], (i + 1) * 1.5);
        }
        double[] out = new double[PedroTuningStore.VALUE_COUNT];
        assertTrue(PedroTuningStore.readInto(out, map));
        for (int i = 0; i < out.length; i++) assertEquals((i + 1) * 1.5, out[i], 0.0);
    }

    /** A file written by an older build is missing keys. Take none of it rather than half. */
    @Test
    public void aPartialBlockIsRejectedWholesale() {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < PedroTuningStore.VALUE_COUNT - 1; i++) {
            map.put(PedroTuningStore.PREFIX + PedroTuningStore.KEYS[i], 1.0);
        }
        assertFalse(PedroTuningStore.readInto(new double[PedroTuningStore.VALUE_COUNT], map));
    }

    @Test
    public void aFileWithNoPedroBlockIsNotAnError() {
        Map<String, Object> map = new HashMap<>();
        map.put("TuningConfig.verboseTelemetry", Boolean.TRUE);
        assertFalse(PedroTuningStore.readInto(new double[PedroTuningStore.VALUE_COUNT], map));
    }

    @Test
    public void nullMapIsRejected() {
        assertFalse(PedroTuningStore.readInto(new double[PedroTuningStore.VALUE_COUNT], null));
    }

    // ---- validation --------------------------------------------------------------

    @Test
    public void plausibleValuesPassValidation() {
        assertTrue(PedroTuningStore.isSane(distinctValues()));
    }

    @Test
    public void nanOrInfinityIsRejected() {
        double[] nan = distinctValues();
        nan[0] = Double.NaN;
        assertFalse(PedroTuningStore.isSane(nan));

        double[] inf = distinctValues();
        inf[8] = Double.POSITIVE_INFINITY;
        assertFalse(PedroTuningStore.isSane(inf));
    }

    /** A corrupt mass would change every feedforward on the robot. */
    @Test
    public void masslessOrAbsurdRobotIsRejected() {
        double[] zero = distinctValues();
        zero[14] = 0.0;
        assertFalse(PedroTuningStore.isSane(zero));

        double[] huge = distinctValues();
        huge[14] = 500.0;
        assertFalse(PedroTuningStore.isSane(huge));
    }

    /** A zero or negative drive velocity means the robot cannot follow a path at all. */
    @Test
    public void nonPositiveDriveVelocityIsRejected() {
        double[] zeroX = distinctValues();
        zeroX[17] = 0.0;
        assertFalse(PedroTuningStore.isSane(zeroX));

        double[] negY = distinctValues();
        negY[18] = -10.0;
        assertFalse(PedroTuningStore.isSane(negY));
    }

    /** Pod offsets are inches from robot center; a metres/millimetres mix-up shows up here. */
    @Test
    public void impossiblePodOffsetIsRejected() {
        double[] v = distinctValues();
        v[19] = 168.75;   // millimetres pasted into an inches field
        assertFalse(PedroTuningStore.isSane(v));
    }

    @Test
    public void wrongLengthArrayIsRejected() {
        assertFalse(PedroTuningStore.isSane(new double[3]));
        assertFalse(PedroTuningStore.isSane(null));
    }
}
