package org.firstinspires.ftc.teamcode.diagnostics;

/**
 * Problem — a named condition a subsystem can report to the DiagnosticsCenter.
 *
 * Convention: define Problem instances as `public static final` constants in the subsystem that
 * emits them. Example:
 *
 *   public class LimelightSubsystem {
 *       public static final Problem NO_TARGET = new Problem(
 *           "LL_NO_TARGET", "Limelight not seeing a target", ProblemSeverity.WARNING);
 *       ...
 *       if (!limelight.hasTarget()) DiagnosticsCenter.reportProblem(NO_TARGET);
 *   }
 *
 * `code` shows up on the Driver Hub; keep it short and grep-friendly. `text` is the human-readable
 * long form for logs.
 */
public class Problem {
    final ProblemSeverity severity;
    final String text;
    final String code;

    /** Constructs a Problem defaulted to ERROR severity. */
    public Problem(String code, String text) {
        this(code, text, ProblemSeverity.ERROR);
    }

    public Problem(String code, String text, ProblemSeverity severity) {
        this.code = code;
        this.text = text;
        this.severity = severity;
    }

    public String getCode() { return code; }
    public String getText() { return text; }
    public ProblemSeverity getSeverity() { return severity; }

    @Override
    public String toString() {
        return String.format("[%s]: %s", code, text);
    }
}
