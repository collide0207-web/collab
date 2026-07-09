package app.collide.control.execution.process;

/**
 * The raw outcome of running one process to completion (or timeout/forced kill).
 * {@code exitCode} is -1 when the process had to be force-killed and never reported one.
 */
public record ProcessResult(
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        int exitCode,
        boolean timedOut,
        long durationMs) {
}
