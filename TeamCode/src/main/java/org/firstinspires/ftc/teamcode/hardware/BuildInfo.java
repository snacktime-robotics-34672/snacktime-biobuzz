package org.firstinspires.ftc.teamcode.hardware;

/**
 * BuildInfo — traceability for snapshots (CLAUDE.md sections 7 and 12).
 *
 * The git commit hash and build time of the running code are stamped into every snapshot
 * (util/Persistence.java) so a saved snapshot always points back to EXACTLY which source produced it.
 *
 * These values are injected at build time by Gradle `buildConfigField` (see build.gradle.additions).
 * If the BuildConfig fields aren't present (e.g. in a unit test), we fall back to "unknown" rather
 * than crashing.
 */
public final class BuildInfo {

    public static final String GIT_HASH = readField("GIT_HASH");
    public static final String BUILD_TIME = readField("BUILD_TIME");

    private static String readField(String name) {
        try {
            // Accessed reflectively so this class also compiles in the test source set,
            // where BuildConfig may not exist.
            Class<?> clazz = Class.forName("org.firstinspires.ftc.teamcode.BuildConfig");
            return (String) clazz.getField(name).get(null);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private BuildInfo() { }
}
