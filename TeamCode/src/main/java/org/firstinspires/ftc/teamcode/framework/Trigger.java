package org.firstinspires.ftc.teamcode.framework;

import com.pedropathing.ivy.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Trigger — turns a condition ("right trigger squeezed", "a problem is active") into command starts
 * and stops. Ivy has no gamepad bindings, so this fills the gap, and it keeps the same method names
 * SolversLib used so a binding reads the same as before.
 *
 *   new Trigger(() -> gamepad1.right_trigger > 0.25).whileActiveOnce(new IntakeCommand(intake));
 *
 * EDGES, NOT LEVELS. Each binding remembers last loop's value and acts only on a change:
 *   - {@link #whenActive}: false→true schedules the command. Release does nothing.
 *   - {@link #whileActiveOnce}: false→true schedules it, true→false cancels it. It does NOT
 *     re-schedule while held, so a command's built-in timeout still counts from the squeeze.
 *
 * POLLED BY {@link IvyOpMode} once per loop, after subsystem periodic() and before commands run —
 * the same order SolversLib used. Every binding is cleared when the OpMode ends.
 *
 * LOOP COST: an index loop over an ArrayList and one supplier call per binding. No allocation
 * (CLAUDE.md §4 rule 8). Build bindings in initialize(), never in the loop.
 */
public class Trigger {

    private static final List<Binding> bindings = new ArrayList<>();

    private final BooleanSupplier condition;

    public Trigger(BooleanSupplier condition) {
        this.condition = condition;
    }

    /** The condition right now. */
    public boolean get() {
        return condition.getAsBoolean();
    }

    /** Schedules the command each time the condition turns true. */
    public Trigger whenActive(Command command) {
        bindings.add(new Binding(condition, command, false));
        return this;
    }

    /** Schedules the command when the condition turns true; cancels it when it turns false. */
    public Trigger whileActiveOnce(Command command) {
        bindings.add(new Binding(condition, command, true));
        return this;
    }

    // ---- Polling (used by IvyOpMode) -----------------------------------------------------------

    /** Checks every binding once. */
    static void pollAll() {
        for (int i = 0; i < bindings.size(); i++) {
            bindings.get(i).poll();
        }
    }

    /** Drops every binding. Called when an OpMode ends. */
    static void clearAll() {
        bindings.clear();
    }

    /** One condition wired to one command. */
    private static final class Binding {
        private final BooleanSupplier condition;
        private final Command command;
        private final boolean cancelOnRelease;
        private boolean wasActive;

        Binding(BooleanSupplier condition, Command command, boolean cancelOnRelease) {
            this.condition = condition;
            this.command = command;
            this.cancelOnRelease = cancelOnRelease;
            // Start from the current value, so a button already held at START does not fire.
            this.wasActive = condition.getAsBoolean();
        }

        void poll() {
            boolean active = condition.getAsBoolean();
            if (active && !wasActive) {
                command.schedule();
            } else if (!active && wasActive && cancelOnRelease) {
                command.cancel();
            }
            wasActive = active;
        }
    }
}
