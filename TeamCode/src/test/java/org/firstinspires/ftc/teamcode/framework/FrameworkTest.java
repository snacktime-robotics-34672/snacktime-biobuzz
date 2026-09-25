package org.firstinspires.ftc.teamcode.framework;

import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.Scheduler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Off-robot tests for the framework layer between our code and Ivy (CLAUDE.md §9).
 *
 * These drive the REAL Ivy scheduler, through the same IvyOpMode.step() the robot runs each loop.
 * They pin down the behaviours our OpModes depend on and that moved from SolversLib to our own code
 * on 2026-09-24: the command lifecycle, subsystem locking, trigger edges, and loop order.
 */
public class FrameworkTest {

    /** Everything that happened, in order, so a test can check sequence as well as counts. */
    private final List<String> log = new ArrayList<>();

    @Before
    public void setUp() {
        IvyOpMode.clearFrameworkState();
    }

    @After
    public void tearDown() {
        IvyOpMode.clearFrameworkState();
    }

    // ---- Test doubles --------------------------------------------------------------------------

    /** A subsystem that records when its periodic() runs. */
    private class FakeSubsystem extends Subsystem {
        final String name;

        FakeSubsystem(String name) {
            this.name = name;
        }

        @Override
        public void periodic() {
            log.add(name + ".periodic");
        }
    }

    /** A command that records its lifecycle and finishes after a set number of loops. */
    private class FakeCommand extends TeamCommand {
        final String name;
        final int loopsToFinish;
        int loops;
        int initializeCount;
        Boolean endedInterrupted; // null until end() runs

        FakeCommand(String name, int loopsToFinish, Object... requirements) {
            this.name = name;
            this.loopsToFinish = loopsToFinish;
            addRequirements(requirements);
        }

        @Override
        public void initialize() {
            initializeCount++;
            loops = 0;
            log.add(name + ".initialize");
        }

        @Override
        public void execute() {
            loops++;
            log.add(name + ".execute");
        }

        @Override
        public boolean isFinished() {
            return loopsToFinish >= 0 && loops >= loopsToFinish;
        }

        @Override
        public void end(boolean interrupted) {
            endedInterrupted = interrupted;
            log.add(name + ".end(" + interrupted + ")");
        }
    }

    // ---- Command lifecycle ---------------------------------------------------------------------

    @Test
    public void commandRunsInitializeOnceThenExecuteUntilFinishedThenEndsCleanly() {
        FakeCommand c = new FakeCommand("c", 2);
        Scheduler.schedule(c);
        assertEquals(1, c.initializeCount);

        IvyOpMode.step(); // loop 1 of 2
        assertTrue(Scheduler.isScheduled(c));
        IvyOpMode.step(); // loop 2 of 2 — finishes
        assertFalse(Scheduler.isScheduled(c));

        assertEquals(1, c.initializeCount);
        assertEquals(Boolean.FALSE, c.endedInterrupted);
    }

    @Test
    public void cancelEndsTheCommandAsInterrupted() {
        FakeCommand c = new FakeCommand("c", -1); // never finishes on its own
        Scheduler.schedule(c);
        IvyOpMode.step();
        c.cancel();
        assertEquals(Boolean.TRUE, c.endedInterrupted);
        assertFalse(Scheduler.isScheduled(c));
    }

    // ---- Subsystem locking (CLAUDE.md §3: two commands can never fight over one motor) ---------

    @Test
    public void aNewCommandOnTheSameSubsystemInterruptsTheOldOne() {
        FakeSubsystem intake = new FakeSubsystem("intake");
        FakeCommand first = new FakeCommand("first", -1, intake);
        FakeCommand second = new FakeCommand("second", -1, intake);

        Scheduler.schedule(first);
        Scheduler.schedule(second);

        assertEquals(Boolean.TRUE, first.endedInterrupted);
        assertFalse(Scheduler.isScheduled(first));
        assertTrue(Scheduler.isScheduled(second));
    }

    @Test
    public void commandsOnDifferentSubsystemsRunTogether() {
        FakeSubsystem intake = new FakeSubsystem("intake");
        FakeSubsystem drive = new FakeSubsystem("drive");
        FakeCommand a = new FakeCommand("a", -1, intake);
        FakeCommand b = new FakeCommand("b", -1, drive);

        Scheduler.schedule(a, b);
        IvyOpMode.step();

        assertTrue(Scheduler.isScheduled(a));
        assertTrue(Scheduler.isScheduled(b));
        assertEquals(null, a.endedInterrupted);
    }

    @Test
    public void anInstantCommandRequiringASubsystemInterruptsWhatWasRunningOnIt() {
        // The shape of Intake.stopCommand(): instant(...).requiring(this).
        FakeSubsystem intake = new FakeSubsystem("intake");
        FakeCommand running = new FakeCommand("running", -1, intake);
        Scheduler.schedule(running);

        Scheduler.schedule(instant(() -> log.add("stop")).requiring(intake));

        assertEquals(Boolean.TRUE, running.endedInterrupted);
        assertTrue(log.contains("stop"));
    }

    // ---- Groups --------------------------------------------------------------------------------

    @Test
    public void sequentialRunsOurCommandsOneAfterAnother() {
        FakeCommand a = new FakeCommand("a", 1);
        FakeCommand b = new FakeCommand("b", 1);
        Command routine = sequential(a, b);

        Scheduler.schedule(routine);
        for (int i = 0; i < 10 && Scheduler.isScheduled(routine); i++) {
            IvyOpMode.step();
        }

        assertFalse(Scheduler.isScheduled(routine));
        assertEquals(Boolean.FALSE, a.endedInterrupted);
        assertEquals(Boolean.FALSE, b.endedInterrupted);
        // b must not start until a has ended.
        assertTrue(log.indexOf("a.end(false)") < log.indexOf("b.initialize"));
    }

    // ---- Triggers ------------------------------------------------------------------------------

    @Test
    public void whileActiveOnceStartsOnPressDoesNotRestartWhileHeldAndCancelsOnRelease() {
        boolean[] held = {false};
        FakeCommand c = new FakeCommand("c", -1);
        new Trigger(() -> held[0]).whileActiveOnce(c);

        IvyOpMode.step();
        assertEquals(0, c.initializeCount);

        held[0] = true;
        IvyOpMode.step();
        IvyOpMode.step();
        IvyOpMode.step();
        // Started once. Restarting every loop would reset IntakeCommand's timeout forever.
        assertEquals(1, c.initializeCount);
        assertTrue(Scheduler.isScheduled(c));

        held[0] = false;
        IvyOpMode.step();
        assertEquals(Boolean.TRUE, c.endedInterrupted);
        assertFalse(Scheduler.isScheduled(c));
    }

    @Test
    public void aButtonAlreadyHeldWhenBoundDoesNotFire() {
        boolean[] held = {true};
        FakeCommand c = new FakeCommand("c", -1);
        new Trigger(() -> held[0]).whenActive(c);

        IvyOpMode.step();
        assertEquals(0, c.initializeCount);
    }

    @Test
    public void whenActiveIgnoresRelease() {
        boolean[] held = {false};
        FakeCommand c = new FakeCommand("c", -1);
        new Trigger(() -> held[0]).whenActive(c);

        held[0] = true;
        IvyOpMode.step();
        held[0] = false;
        IvyOpMode.step();

        assertEquals(1, c.initializeCount);
        assertTrue(Scheduler.isScheduled(c));
    }

    // ---- Loop order and clean-up ---------------------------------------------------------------

    @Test
    public void subsystemsRegisterThemselvesAndPeriodicRunsBeforeCommands() {
        new FakeSubsystem("sub");
        FakeCommand c = new FakeCommand("c", -1);
        Scheduler.schedule(c);
        log.clear();

        IvyOpMode.step();

        assertEquals("sub.periodic", log.get(0));
        assertEquals("c.execute", log.get(1));
    }

    @Test
    public void registeringTheSameSubsystemTwiceRunsItOnce() {
        FakeSubsystem sub = new FakeSubsystem("sub");
        Subsystem.register(sub);
        assertEquals(1, Subsystem.registeredCount());
    }

    @Test
    public void clearingStateLeavesNothingForTheNextOpMode() {
        new FakeSubsystem("sub");
        boolean[] held = {false};
        FakeCommand bound = new FakeCommand("bound", -1);
        new Trigger(() -> held[0]).whileActiveOnce(bound);
        FakeCommand running = new FakeCommand("running", -1);
        Scheduler.schedule(running);

        IvyOpMode.clearFrameworkState();
        log.clear();
        held[0] = true;
        IvyOpMode.step();

        assertEquals(0, Subsystem.registeredCount());
        assertFalse(Scheduler.isScheduled(running));
        assertEquals(0, bound.initializeCount);
        assertTrue(log.isEmpty());
    }
}
