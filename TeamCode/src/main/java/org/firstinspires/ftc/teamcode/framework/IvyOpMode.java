package org.firstinspires.ftc.teamcode.framework;

import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.Scheduler;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

/**
 * IvyOpMode — base class for every OpMode that runs commands. It replaced SolversLib's
 * CommandOpMode on 2026-09-24 and keeps the same shape, so an OpMode still writes three methods:
 *
 *   initialize()  once, at INIT: build hardware, bind triggers, schedule the auto routine.
 *   run()         every loop after START. Clear the bulk cache first (§4), do your own work, and
 *                 call super.run() to step the robot (see {@link #run()}).
 *   reset()       once, at stop: stop motors, save tuning, then call super.reset().
 *
 * WHAT ONE STEP DOES, in this order (the order SolversLib used, and the order the code relies on):
 *   1. every Subsystem's periodic()  — sensors read, health published
 *   2. every Trigger binding         — commands started or cancelled from the gamepad
 *   3. Ivy's Scheduler.execute()     — every running command advances
 *
 * STATE IS CLEARED at both ends of an OpMode. Ivy's Scheduler is a static class inside a library,
 * so it is NOT reloaded by Sloth and it survives from one OpMode to the next. Clearing it at the
 * start too means a crashed OpMode can never leave a command running into the next one.
 *
 * LOOP COST — flagged per CLAUDE.md §0: Ivy's Scheduler.execute() allocates two small ArrayDeques
 * on every call. That is inside the library and we cannot change it. It is a few dozen bytes per
 * loop, well under what a telemetry string costs, but it is not zero. If Loop Hz ever shows GC
 * hitches, the Android Profiler (§14) will show whether this is part of it.
 */
public abstract class IvyOpMode extends LinearOpMode {

    /** Build hardware and bindings, and schedule any auto routine. */
    public abstract void initialize();

    /** One loop. Subclasses override, clear the bulk cache first, and call super.run(). */
    public void run() {
        step();
    }

    /** One step of the robot, in the order above. Static so the unit tests run the same code. */
    static void step() {
        Subsystem.runAllPeriodic();
        Trigger.pollAll();
        Scheduler.execute();
    }

    /** Clean-up at stop. Subclasses override, stop their motors, and call super.reset() last. */
    public void reset() {
        clearFrameworkState();
    }

    /** Starts commands now. */
    public void schedule(Command... commands) {
        Scheduler.schedule(commands);
    }

    /**
     * Adds a subsystem that was NOT built in this OpMode, such as the DiagnosticsCenter singleton.
     * Subsystems built here register themselves.
     */
    public void register(Subsystem... subsystems) {
        for (Subsystem s : subsystems) {
            Subsystem.register(s);
        }
    }

    @Override
    public void runOpMode() throws InterruptedException {
        clearFrameworkState();
        initialize();
        try {
            // An OpMode may run its own init loop inside initialize() (the auto menu does); if it
            // did, START or STOP has already happened by the time we get here.
            while (opModeInInit()) {
                idle();
            }
            while (opModeIsActive()) {
                run();
            }
        } finally {
            reset();
        }
    }

    /** Empties Ivy's scheduler, the subsystem list, and every trigger binding. */
    static void clearFrameworkState() {
        Scheduler.reset();
        Subsystem.clearRegistry();
        Trigger.clearAll();
    }
}
