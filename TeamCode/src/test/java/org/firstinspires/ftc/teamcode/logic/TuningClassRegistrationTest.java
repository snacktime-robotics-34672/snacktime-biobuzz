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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
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

    /**
     * The Pedro tuning suite. These are BENCH TEST SETTINGS, not robot tuning: how far the Drive
     * test drives, how far you spin for the Turn test, the radius of the Circle test. You set one
     * for the run in front of you, and it SHOULD go back to its default next session — persisting
     * them would quietly carry a 12-inch bench distance into the next tuning session and make the
     * test lie about the robot. They all live in Tuning.java.
     *
     * They are a separate list from INTENTIONALLY_NOT_PERSISTED on purpose. That list is for
     * one-off exceptions, each argued individually; this one is a single category with one reason,
     * so the suite growing a new tuner does not read as the excuse list growing a new excuse.
     */
    private static final Set<String> BENCH_TUNERS = new HashSet<>(Arrays.asList(
            "ForwardTuner", "LateralTuner", "TurnTuner",
            "ForwardVelocityTuner", "LateralVelocityTuner",
            "ForwardZeroPowerAccelerationTuner", "LateralZeroPowerAccelerationTuner",
            "TranslationalTuner", "HeadingTuner", "DriveTuner",
            "Line", "CentripetalTuner", "Circle"
    ));

    /** Every class allowed to be @Configurable without being persisted. */
    private static Set<String> excused() {
        Set<String> all = new HashSet<>(INTENTIONALLY_NOT_PERSISTED);
        all.addAll(BENCH_TUNERS);
        return all;
    }

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
                found.addAll(configurableClassesIn(
                        new String(Files.readAllBytes(p), StandardCharsets.UTF_8)));
            }
        }
        return found;
    }

    /** A type declaration, after any modifiers. Group 1 is the name. */
    private static final Pattern TYPE_DECL = Pattern.compile(
            "^(?:(?:public|protected|private|abstract|final|static|strictfp)\\s+)*"
                    + "(?:class|interface|enum)\\s+(\\w+)");

    /**
     * Names every class in one source file that carries a class-level @Configurable.
     *
     * WHY THIS IS NOT THE FILE NAME: it used to be. One file can hold many classes — Tuning.java
     * holds sixteen — so keying off the file name saw the whole suite as the single class "Tuning",
     * which was already excused. Thirteen tuner classes were @Configurable and unpersisted for a
     * whole session and this test stayed green, which is the exact failure it exists to catch.
     *
     * So: find each @Configurable on a line of its own, then read forward to the declaration it
     * annotates, stepping over further annotations, blank lines and comments. If what it annotates
     * is not a type — a field, say — nothing is recorded.
     */
    static List<String> configurableClassesIn(String source) {
        List<String> found = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (inBlockComment) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            if (!line.equals("@Configurable")) continue;
            String name = declaredTypeAfter(lines, i + 1);
            if (name != null) found.add(name);
        }
        return found;
    }

    /** The name of the first type declared at or after {@code start}, or null if it is not a type. */
    private static String declaredTypeAfter(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            boolean skippable = line.isEmpty() || line.startsWith("@") || line.startsWith("//")
                    || line.startsWith("*") || line.startsWith("/*");
            if (skippable) continue;
            Matcher m = TYPE_DECL.matcher(line);
            // The first real line settles it: an annotation on anything but a type declares nothing.
            return m.find() ? m.group(1) : null;
        }
        return null;
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
        Set<String> excused = excused();

        List<String> unregistered = new ArrayList<>();
        for (String cls : configurable) {
            if (!registered.contains(cls) && !excused.contains(cls)) {
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
        for (String name : excused()) {
            assertTrue("The excuse lists name " + name + ", which is no longer an @Configurable "
                            + "class — remove it from INTENTIONALLY_NOT_PERSISTED or BENCH_TUNERS",
                    configurable.contains(name));
        }
    }

    // ---- the scan itself, off the source tree ----------------------------------------------

    @Test
    public void findsEveryAnnotatedClassInOneFile() {
        String src = "package p;\n"
                + "@Configurable\n"
                + "public class First {}\n"
                + "class NotAnnotated {}\n"
                + "@Configurable\n"
                + "class Second extends OpMode {}\n";
        assertEquals(Arrays.asList("First", "Second"), configurableClassesIn(src));
    }

    /** The regression this rewrite fixes: one file, many classes, only some annotated. */
    @Test
    public void doesNotFallBackToTheFileName() {
        String src = "@Configurable\npublic class Outer {}\nclass Inner {}\n";
        assertEquals(Arrays.asList("Outer"), configurableClassesIn(src));
    }

    @Test
    public void stepsOverOtherAnnotationsAndComments() {
        String src = "@Configurable\n"
                + "@TeleOp(name = \"Tuning\", group = \"Pedro Pathing\")\n"
                + "// a comment in the way\n"
                + "public class Tuning extends SelectableOpMode {}\n";
        assertEquals(Arrays.asList("Tuning"), configurableClassesIn(src));
    }

    @Test
    public void ignoresMentionsInsideComments() {
        String src = "/*\n"
                + "@Configurable\n"
                + "*/\n"
                + "class NotAnnotated {}\n"
                + "/** WHY @Configurable: because. */\n"
                + "class AlsoNot {}\n";
        assertTrue(configurableClassesIn(src).isEmpty());
    }

    @Test
    public void ignoresAnAnnotationOnAField() {
        String src = "class Holder {\n    @Configurable\n    public static double x = 1;\n}\n";
        assertTrue(configurableClassesIn(src).isEmpty());
    }

    @Test
    public void findsEnumsAndInterfacesToo() {
        String src = "@Configurable\nenum Mode { A }\n@Configurable\ninterface Knobs {}\n";
        assertEquals(Arrays.asList("Mode", "Knobs"), configurableClassesIn(src));
    }
}
