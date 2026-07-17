package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.config.FieldTweaks;
import org.firstinspires.ftc.teamcode.util.TelemetryMenu;

/**
 * AutonMenu — pre-match selection UI for autonomous. Renders on the Driver Hub during init_loop
 * (or the CommandOpMode init phase) so the driver picks alliance, start pose, and pre-match delay
 * WITHOUT needing a laptop connected.
 *
 * WHY: CLAUDE.md §9 says "Alliance and starting pose are chosen in exactly ONE place and passed
 * down — no auto hardcodes its own — so the robot never runs from the wrong pose." Before this,
 * AutonomousExample had `ALLIANCE = "RED"` baked into the source, meaning every alliance switch
 * needed a Sloth reload. Now the driver just twiddles the dpad.
 *
 * PATTERN COPIED FROM: FTC 5327 SMS Robotics' decode-2025
 * (team/opmodes/auton/support/AutonMenu.java). Their menu had NONE/FIELD_ONE/FIELD_TWO/PRACTICE
 * for their field-tweak system; ours has Alliance and StartPose to match how BIOBUZZ decisions
 * naturally split. Delay is a plain integer in seconds (0-15) — decode-2025 used 250ms steps via
 * their own IntegerMultipleOption; we don't need that granularity yet.
 */
public class AutonMenu {

    /** Which alliance we're playing for — flips path mirroring and target-side selection. */
    public enum Alliance {
        RED,
        BLUE
    }

    /**
     * Placeholder starting positions; adjust to real names once the game reveals which starting
     * tiles the field allows. LEFT/CENTER/RIGHT are generic and safe as placeholders.
     */
    public enum StartPose {
        LEFT,
        CENTER,
        RIGHT
    }

    private final TelemetryMenu menu;
    private final TelemetryMenu.EnumOption allianceOption;
    private final TelemetryMenu.EnumOption startPoseOption;
    private final TelemetryMenu.EnumOption fieldOption;
    private final TelemetryMenu.IntegerOption delaySecondsOption;

    public AutonMenu(Telemetry telemetry) {
        TelemetryMenu.MenuElement root = new TelemetryMenu.MenuElement("Auton Config", true);

        allianceOption = new TelemetryMenu.EnumOption("Alliance", Alliance.values(), Alliance.RED);
        startPoseOption = new TelemetryMenu.EnumOption("Start Pose", StartPose.values(), StartPose.LEFT);
        fieldOption = new TelemetryMenu.EnumOption("Field", FieldTweaks.Field.values(), FieldTweaks.Field.PRACTICE);
        delaySecondsOption = new TelemetryMenu.IntegerOption("Delay (s)", 0, 15, 0);

        root.addChild(allianceOption);
        root.addChild(startPoseOption);
        root.addChild(fieldOption);
        root.addChild(delaySecondsOption);

        this.menu = new TelemetryMenu(telemetry, root);
    }

    /** Call from init_loop / init phase to draw the menu and process gamepad input. */
    public void loop(Gamepad gamepad) {
        menu.loop(gamepad);
    }

    public Alliance getAlliance() {
        return (Alliance) allianceOption.getValue();
    }

    public StartPose getStartPose() {
        return (StartPose) startPoseOption.getValue();
    }

    public FieldTweaks.Field getField() {
        return (FieldTweaks.Field) fieldOption.getValue();
    }

    public int getDelaySeconds() {
        return delaySecondsOption.getValue();
    }

    /** Convenience: fetch the tweaks for the currently-selected alliance + field. */
    public org.firstinspires.ftc.teamcode.config.AutonFieldTweaks getFieldTweaks() {
        return FieldTweaks.lookup(getAlliance() == Alliance.RED, getField());
    }
}
