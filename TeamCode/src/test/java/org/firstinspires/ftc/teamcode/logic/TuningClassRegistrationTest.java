package org.firstinspires.ftc.teamcode.logic;

import org.firstinspires.ftc.teamcode.util.Persistence;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Fails the build when an @Configurable class is not registered for persistence.
 *
 * WHY THIS TEST EXISTS: marking a class @Configurable makes Panels show its tunables and lets you
 * turn them. Registering it in Persistence.TUNING_CLASSES is what makes those values survive a
 * stop. The two are separate steps, and forgetting the second one produces the worst kind of bug —
 * the knob appears in the dashboard, changes the robot, and silently vanishes on every stop, with
 * nothing anywhere reporting a problem. CLAUDE.md §6 says to register each class at kickoff, but a
 * process step guarding an invisible failure is a step people skip.
 *
 * This test reads the source tree rather than the classpath, so it sees exactly what a reviewer
 * would see and does not depend on the build layout.
 */
public class TuningClassRegistrationTest {

    /**
     * Classes that are @Configurable but deliberately NOT persisted. Each needs a reason — this set
     * is the pressure valve, so it must stay small and justified rather than becoming a dumping
     * ground for anything that makes the test red.
     */
    private static final Set<String> INTENTIONALLY_NOT_PERSISTED = new HashSet<>(Arrays.asList(
            // Pedro constants live in nested Pedro types that reflection cannot restore. They are
            // persisted by PedroTuningStore's explicit table instead — see its class doc.
            "Constants",
            // The tuner OpMode. It is @Configurable so Panels groups the suite; its own statics are
            // the follower and telemetry, all marked @IgnoreConfigurable.
            "Tuning",
            // A one-field diagnostic canary for the Panels/Sloth class-identity bug. You type into
            // it to prove live tuning works; persisting it would be meaningless.
            "PanelsProbe"
    ));

    /** Walks up from the working directory to find the teamcode source root. */
    private static Path teamcodeSource() {
        File dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            File candidate = new File(dir, "src/main/java/org/firstinspires/ftc/teamcode");
            if (candidate.isDirectory()) return candidate.toPath();
            File nested = new File(dir, "TeamCode/src/main/java/org/firstinspires/ftc/teamcode");
            if (nested.isDirectory()) return nested.toPath();
        }
        return null;
    }

    /** Every class carrying a class-level @Configurable annotation, by simple name. */
    private static List<String> configurableClasses(Path root) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                // Class-level only: the annotation sits in column 0, directly above the declaration.
                // A mention inside a comment or on a field is indented and does not count.
                if (src.contains("\n@Configurable")) {
                    String name = p.getFileName().toString();
                    found.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        return found;
    }

    @Test
    public void everyConfigurableClassIsRegisteredOrExcusedByName() throws IOException {
        Path root = teamcodeSource();
        assertTrue("could not locate the teamcode source tree from " + new File(".").getAbsolutePath(),
                root != null);

        List<String> configurable = configurableClasses(root);
        assertTrue("found no @Configurable classes at all — the scan is broken, not the code",
                configurable.size() >= 5);

        Set<String> registered = Persistence.tuningClassSimpleNames();

        List<String> unregistered = new ArrayList<>();
        for (String cls : configurable) {
            if (!registered.contains(cls) && !INTENTIONALLY_NOT_PERSISTED.contains(cls)) {
                unregistered.add(cls);
            }
        }

        if (!unregistered.isEmpty()) {
            fail("These classes are @Configurable but not registered for persistence, so their "
                    + "tunables silently vanish on every OpMode stop: " + unregistered
                    + "\nAdd each to Persistence.TUNING_CLASSES, or — if it genuinely should not "
                    + "persist — add it to INTENTIONALLY_NOT_PERSISTED in this test with a reason.");
        }
    }

    /** The excuse list must not name classes that no longer exist, or it stops meaning anything. */
    @Test
    public void everyExcusedClassStillExists() throws IOException {
        Path root = teamcodeSource();
        assertTrue(root != null);
        List<String> configurable = configurableClasses(root);
        for (String excused : INTENTIONALLY_NOT_PERSISTED) {
            assertTrue("INTENTIONALLY_NOT_PERSISTED names " + excused + ", which is no longer an "
                            + "@Configurable class — remove it from the list",
                    configurable.contains(excused));
        }
    }
}
