package org.firstinspires.ftc.teamcode.diagnostics;

import static java.util.Objects.isNull;

import com.qualcomm.robotcore.util.RobotLog;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.command.button.Trigger;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.config.TuningConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DiagnosticsCenter — a singleton subsystem where any code can report a Problem, and one line in
 * the OpMode drains the current problems to Driver Hub telemetry.
 *
 * WHY: CLAUDE.md §5 requires "per-subsystem health telemetry ... enough state to spot a fault by
 * watching, not by reading code." Without a central place, every subsystem invents its own
 * ad-hoc telemetry lines and the Driver Hub becomes noise. With this, subsystems emit typed
 * Problem objects; the Center dedupes and expires them; one call in the OpMode renders them.
 *
 * USAGE:
 *   // in any subsystem, when something goes wrong:
 *   DiagnosticsCenter.reportProblem(MY_PROBLEM, extraData);
 *
 *   // in TeleOp/Auto run():
 *   DiagnosticsCenter.telemetry(telemetry);
 *
 * Expired problems are cleaned in periodic() using `TuningConfig.diagnosticsProblemExpireSeconds`.
 *
 * Ported from FTC 5327 SMS Robotics' decode-2025 (common/diagnostics/DiagnosticsCenter.java).
 * Adapted:
 * - `GlobalConfig.DIAGNOSTICS_PROBLEM_EXPIRE_S` → `TuningConfig.diagnosticsProblemExpireSeconds`
 * - Removed the `androidx.annotation.NonNull` import; not worth an extra dep for one annotation
 * - Kept the emoji severity icons since Driver Hub telemetry renders them fine
 */
public class DiagnosticsCenter extends SubsystemBase {
    private static DiagnosticsCenter instance;
    private final HashMap<Problem, ProblemInstance> latestIssueByCode;

    DiagnosticsCenter() {
        latestIssueByCode = new HashMap<>();
    }

    /** @return a SolversLib Trigger that fires while any problem is active — useful for LEDs, rumble. */
    public Trigger makeHasIssueTrigger() {
        return new Trigger(() -> !latestIssueByCode.isEmpty());
    }

    public static DiagnosticsCenter get() {
        if (isNull(instance)) {
            instance = new DiagnosticsCenter();
        }
        return instance;
    }

    /** Convenience for OpModes: one call per loop drains all current problems to telemetry. */
    public static void telemetry(Telemetry telemetry) {
        get().printTelemetry(telemetry);
    }

    private final Map<ProblemSeverity, String> severityToTextMap = Map.of(
            ProblemSeverity.INFO, "🔵",
            ProblemSeverity.WARNING, "⚠️",
            ProblemSeverity.ERROR, "🟥"
    );

    public void printTelemetry(Telemetry telemetry) {
        for (ProblemInstance problem : latestIssueByCode.values()) {
            String key = String.format("%s %s", severityToTextMap.get(problem.getSeverity()), problem.getCode());
            telemetry.addData(key, String.format("[%.2fs ago] %s", problem.getHowLongAgoInSeconds(), problem.getDataString()));
        }
    }

    /** Report a problem. `data` is optional context (values, sensor readings) rendered next to it. */
    public static void reportProblem(Problem code, Object... data) {
        get().internalReportProblem(code, data);
    }

    private void internalReportProblem(Problem code, Object... data) {
        ProblemInstance problemInstance = new ProblemInstance(System.currentTimeMillis(), code, data);
        ProblemInstance previousProblem = latestIssueByCode.put(code, problemInstance);

        // Only log new occurrences of a given code — avoids flooding logs when a subsystem reports
        // the same problem every loop.
        if (previousProblem != null) {
            if (!previousProblem.isSameProblem(problemInstance)) {
                RobotLog.e("PROBLEM: %s", problemInstance);
            }
        }
    }

    @Override
    public void periodic() {
        latestIssueByCode.entrySet().removeIf(entry ->
                entry.getValue().isOlderThan(TuningConfig.diagnosticsProblemExpireSeconds));
    }

    public Optional<ProblemInstance> getLastProblem() {
        return latestIssueByCode.entrySet().stream()
                .sorted((a, b) -> Double.compare(a.getValue().timestamp, b.getValue().timestamp))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public void reset() {
        RobotLog.i("Clearing diagnostic codes");
        latestIssueByCode.clear();
    }

    /** One occurrence of a Problem, with timestamp + optional context data. */
    public static class ProblemInstance {
        private final double timestamp;
        private final Problem problemDefinition;
        private final Object[] data;

        public ProblemInstance(long timestamp, Problem problemDefinition, Object[] data) {
            this.timestamp = timestamp;
            this.problemDefinition = problemDefinition;
            this.data = data;
        }

        public boolean isSameProblem(ProblemInstance o) {
            if (this == o) return true;
            if (o == null) return false;
            return Objects.equals(problemDefinition, o.problemDefinition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, problemDefinition, Arrays.hashCode(data));
        }

        public boolean isOlderThan(long durationS) {
            long durationMs = durationS * 1000;
            double now = System.currentTimeMillis();
            return (now - durationMs) >= this.timestamp;
        }

        public double getHowLongAgoInSeconds() {
            double now = System.currentTimeMillis();
            return (now - this.timestamp) / 1000.0;
        }

        @Override
        public String toString() {
            return String.format("Problem{timestamp=%s, code=%s, data=%s}",
                    timestamp, problemDefinition, getDataString());
        }

        public String getCode() { return problemDefinition.code; }
        public String getSpokenText() { return problemDefinition.text; }
        public ProblemSeverity getSeverity() { return problemDefinition.severity; }
        public Object[] getData() { return data; }
        public String getDataString() { return data == null ? "" : Arrays.toString(data); }
    }
}
