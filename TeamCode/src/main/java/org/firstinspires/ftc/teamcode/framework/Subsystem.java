package org.firstinspires.ftc.teamcode.framework;

import java.util.ArrayList;
import java.util.List;

/**
 * Subsystem — base class for a mechanism that owns hardware (Drivetrain, Intake, Vision ...).
 *
 * WHY WE HAVE OUR OWN: Ivy, our command scheduler, has no idea of a subsystem. It only knows
 * "requirements", which can be any object. So a subsystem is just an object that commands name as a
 * requirement ({@code .requiring(intake)}), plus a {@link #periodic()} that runs once every loop.
 * This class supplies the second half.
 *
 * IT REGISTERS ITSELF. Constructing a subsystem adds it to the list {@link IvyOpMode} walks each
 * loop, exactly as SolversLib's SubsystemBase did. An OpMode never has to remember to register one.
 * The list is cleared when the OpMode ends, so the next OpMode starts empty.
 *
 * THE ONE EXCEPTION is a subsystem that outlives an OpMode, such as the DiagnosticsCenter
 * singleton. It was built by an earlier OpMode, so its constructor does not run again. Such an
 * OpMode calls {@link IvyOpMode#register} to add it back.
 *
 * LOOP COST: the per-loop walk is an index loop over an ArrayList, so it allocates nothing
 * (CLAUDE.md §4 rule 8).
 */
public abstract class Subsystem {

    private static final List<Subsystem> registered = new ArrayList<>();

    protected Subsystem() {
        register(this);
    }

    /** Runs once per loop, before any command. Read sensors or publish health here. */
    public void periodic() {
    }

    // ---- Registry (used by IvyOpMode) ----------------------------------------------------------

    /** Adds a subsystem to the per-loop list. Adding the same one twice is a no-op. */
    static void register(Subsystem subsystem) {
        if (!registered.contains(subsystem)) {
            registered.add(subsystem);
        }
    }

    /** Calls every registered subsystem's periodic(). */
    static void runAllPeriodic() {
        for (int i = 0; i < registered.size(); i++) {
            registered.get(i).periodic();
        }
    }

    /** Empties the list. Called when an OpMode ends. */
    static void clearRegistry() {
        registered.clear();
    }

    /** How many subsystems are registered. For tests and bench telemetry. */
    static int registeredCount() {
        return registered.size();
    }
}
