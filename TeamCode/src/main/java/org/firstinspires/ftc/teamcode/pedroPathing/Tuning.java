package org.firstinspires.ftc.teamcode.pedroPathing;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.Tuner;

import org.firstinspires.ftc.teamcode.pedroPathing.procedures.ForesightTuner;
import org.firstinspires.ftc.teamcode.pedroPathing.procedures.MecanumTuner;
import org.firstinspires.ftc.teamcode.pedroPathing.procedures.PinpointTuner;
import org.firstinspires.ftc.teamcode.pedroPathing.procedures.Tests;
import org.firstinspires.ftc.teamcode.util.RobotIdentity;

/**
 * Tuning — the list of tuning procedures AutoTune offers, and how each one is wired to OUR robot.
 *
 * HOW TO RUN IT: there is no OpMode. Pedro 3 starts a web server on the robot as soon as the Robot
 * Controller app starts, and AutoTune is a page on it — connect to the robot's Wi-Fi and open the
 * address Pedro's docs give for the hub. Each method below appears there as a button.
 *
 * WHAT REPLACED WHAT: this file used to be ~1500 lines holding sixteen tuner OpModes copied out of
 * Pedro 2. Pedro 3 ships that machinery itself, so all that is left here is the wiring: which
 * procedures we want, and which robot's hardware they should build.
 *
 * THE PROCEDURES THEMSELVES live in {@code pedroPathing/procedures/} and are UPSTREAM CODE, copied
 * from the Pedro Quickstart unchanged apart from the package line. Do not edit them by hand — take
 * a newer copy from the Quickstart instead, the same way we treated the old tuner suite.
 *
 * EVERY TUNER RUNS ON THE ROBOT YOU ARE STANDING AT. The hardware comes from {@link Constants},
 * which picks this robot's own values by network name, so tuning the test bot can never write the
 * comp robot's numbers (CLAUDE.md §6).
 *
 * WHAT TO DO WITH THE RESULT: each procedure prints a paste-ready block of Java. Put its numbers in
 * the matching fields in {@link Constants} for the robot you tuned, set that robot's
 * {@code ...PedroTuned} flag true so it stops being capped to half power, and commit. You can also
 * turn the same values in Panels and commit that robot's tuning file (§6) — the fields are ordinary
 * tunables now.
 */
public class Tuning {

    /** Finds which way each drive motor has to spin. Run this first on a new chassis. */
    @Tuner(name = "Mecanum Directions")
    public static Procedure mecanumDirections() {
        return new MecanumTuner();
    }

    /** Finds the Pinpoint pod directions, offsets, and scalars. */
    @Tuner(name = "Pinpoint Localizer")
    public static Procedure pinpoint() {
        return new PinpointTuner();
    }

    /**
     * Measures everything Foresight needs to follow a path: velocities, natural decelerations, the
     * brake coefficients, and the translational and heading gains. THIS is the one that unblocks
     * Pedro 3 on a robot — without its numbers the robot stays capped at half power (see Constants).
     */
    @Tuner(name = "Foresight (path following)")
    public static Procedure foresight() {
        return new ForesightTuner(Constants::localizerFor, Constants::drivetrainFor);
    }

    /** Line, curve, hold and localization checks — run these to confirm a tune actually worked. */
    @Tuner(name = "Tests")
    public static Procedure tests() {
        return new Tests(
                Constants::drivetrainFor,
                Constants::localizerFor,
                () -> new Foresight(Constants.foresightFor(RobotIdentity.resolve())));
    }
}
