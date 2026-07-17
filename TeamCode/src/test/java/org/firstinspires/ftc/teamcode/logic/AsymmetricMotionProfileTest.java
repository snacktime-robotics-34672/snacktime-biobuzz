package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.profile.AsymmetricMotionProfile;
import org.firstinspires.ftc.teamcode.util.profile.ProfileConstraints;
import org.firstinspires.ftc.teamcode.util.profile.ProfileState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Off-robot tests for the motion profile math. These verify the invariants that make the profile
 * safe to use (start at start, end at end, stay within limits), not exact interior samples.
 */
public class AsymmetricMotionProfileTest {

    private static final double DELTA = 0.001;

    @Test
    public void profileStartsAtInitialPosition() {
        ProfileConstraints c = new ProfileConstraints(10.0, 5.0, 5.0);
        AsymmetricMotionProfile p = new AsymmetricMotionProfile(0, 100, c);
        ProfileState s = p.calculate(0);
        assertEquals(0.0, s.x, DELTA);
        assertEquals(0.0, s.v, DELTA);
    }

    @Test
    public void profileEndsAtFinalPosition() {
        ProfileConstraints c = new ProfileConstraints(10.0, 5.0, 5.0);
        AsymmetricMotionProfile p = new AsymmetricMotionProfile(0, 100, c);
        ProfileState s = p.calculate(p.totalTime);
        assertEquals(100.0, s.x, DELTA);
        assertEquals(0.0, s.v, DELTA);
    }

    @Test
    public void pastTotalTime_clampsToFinal() {
        ProfileConstraints c = new ProfileConstraints(10.0, 5.0, 5.0);
        AsymmetricMotionProfile p = new AsymmetricMotionProfile(0, 100, c);
        ProfileState s = p.calculate(p.totalTime + 5.0);
        assertEquals(100.0, s.x, DELTA);
    }

    @Test
    public void reverseMove_endsAtNegativeTarget() {
        ProfileConstraints c = new ProfileConstraints(10.0, 5.0, 5.0);
        AsymmetricMotionProfile p = new AsymmetricMotionProfile(50, 10, c);
        ProfileState s = p.calculate(p.totalTime);
        assertEquals(10.0, s.x, DELTA);
    }

    @Test
    public void asymmetricLimits_shorterTotalTimeWhenDecelIsHigher() {
        // Same distance, same accel; higher decel should finish sooner.
        ProfileConstraints slow = new ProfileConstraints(10.0, 5.0, 5.0);
        ProfileConstraints fastDecel = new ProfileConstraints(10.0, 5.0, 20.0);
        AsymmetricMotionProfile a = new AsymmetricMotionProfile(0, 100, slow);
        AsymmetricMotionProfile b = new AsymmetricMotionProfile(0, 100, fastDecel);
        assertTrue("Higher decel should mean faster total time", b.totalTime < a.totalTime);
    }

    @Test
    public void triangularProfile_whenDistanceTooShortForCruise() {
        // Very short move, high velo cap — should never reach cruise; peak velocity < constraints.velo.
        ProfileConstraints c = new ProfileConstraints(100.0, 10.0, 10.0);
        AsymmetricMotionProfile p = new AsymmetricMotionProfile(0, 1, c);
        assertEquals("Triangular profile has zero cruise time", 0.0, p.t2, DELTA);
        assertTrue("Peak velocity should be under cap for a triangular profile",
                p.max_velocity < c.velo);
    }

    @Test
    public void constraints_takeAbsoluteValues() {
        ProfileConstraints c = new ProfileConstraints(-10.0, -5.0, -5.0);
        assertEquals(10.0, c.velo, DELTA);
        assertEquals(5.0, c.accel, DELTA);
        assertEquals(5.0, c.decel, DELTA);
    }
}