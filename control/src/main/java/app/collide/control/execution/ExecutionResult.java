package app.collide.control.execution;

import app.collide.control.execution.model.ExecutionStatus;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessResult;
import java.util.UUID;

/** Wire shape returned by POST /execute, GET /result/{id}. */
public record ExecutionResult(
        String executionId,
        String language,
        ExecutionStatus status,
        String stdout,
        String stderr,
        Integer exitCode,
        long executionTimeMs,
        boolean stdoutTruncated,
        boolean stderrTruncated) {

    public static ExecutionResult compileFailure(UUID executionId, Language language, ProcessResult compile) {
        ExecutionStatus status = compile.timedOut() ? ExecutionStatus.TIMEOUT : ExecutionStatus.FAILED;
        return new ExecutionResult(
                executionId.toString(),
                language.name(),
                status,
                compile.stdout(),
                compile.stderr(),
                compile.timedOut() ? null : compile.exitCode(),
                compile.durationMs(),
                compile.stdoutTruncated(),
                compile.stderrTruncated());
    }

    public static ExecutionResult of(UUID executionId, Language language, ProcessResult run) {
        ExecutionStatus status = run.timedOut()
                ? ExecutionStatus.TIMEOUT
                : run.exitCode() == 0 ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;
        return new ExecutionResult(
                executionId.toString(),
                language.name(),
                status,
                run.stdout(),
                run.stderr(),
                run.timedOut() ? null : run.exitCode(),
                run.durationMs(),
                run.stdoutTruncated(),
                run.stderrTruncated());
    }

    public static ExecutionResult cancelled(UUID executionId, Language language) {
        return new ExecutionResult(
                executionId.toString(), language.name(), ExecutionStatus.CANCELLED, null, null, null, 0, false, false);
    }

    /** A failure before/outside the child process itself (e.g. workspace I/O), not a program error. */
    public static ExecutionResult error(UUID executionId, Language language, String message) {
        return new ExecutionResult(
                executionId.toString(), language.name(), ExecutionStatus.FAILED, null, message, null, 0, false, false);
    }

    public static ExecutionResult pending(UUID executionId, Language language) {
        return new ExecutionResult(
                executionId.toString(), language.name(), ExecutionStatus.PENDING, null, null, null, 0, false, false);
    }
}
