package org.firstinspires.ftc.teamcode.config;

/**
 * AutonFieldTweaks — per-field, per-alliance pose deltas so drift discovered on one field doesn't
 * force retuning every path from scratch.
 *
 * WHY: Two competition fields are never *exactly* the same — small differences in tape lines, wall
 * squareness, or lighting for vision cause an autonomous that works perfectly on Field 1 to drift a
 * few inches on Field 2. Rather than retuning the whole plan, apply a global offset to the starting
 * pose (or waypoints) for that field/alliance combo. Tune the delta live via Panels between
 * matches, then re-run the same path.
 *
 * PATTERN COPIED FROM: FTC 5327 SMS Robotics' decode-2025
 * (team/opmodes/auton/support/AutonFieldTweaks.java). Their template was intentionally empty; ours
 * seeds with three fields (X/Y offset in inches, heading offset in degrees) that cover the common
 * cases. Add more fields at kickoff as you find them useful.
 *
 * HOW TO USE:
 *   AutonFieldTweaks tweaks = FieldTweaks.lookup(menu.getAlliance(), menu.getField());
 *   Pose startPose = basePose.plus(new Pose(tweaks.xOffsetInches, tweaks.yOffsetInches,
 *                                            Math.toRadians(tweaks.headingOffsetDeg)));
 */
public class AutonFieldTweaks {
    public double xOffsetInches = 0.0;
    public double yOffsetInches = 0.0;
    public double headingOffsetDeg = 0.0;

    // TEAM SETUP: add more per-field tunable variables here as you discover what needs tweaking
    // between fields — e.g. a second-waypoint delta, a target-side delta, etc.
}
