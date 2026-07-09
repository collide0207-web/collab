package app.collide.control.execution.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Runs one command to completion, isolated to a working directory, bounded by a timeout.
 * Always invoked with an argument list (never a shell string) so user-controlled input is
 * never interpreted as shell syntax. This is the seam where Docker/container execution
 * later replaces "run this process on the host" — callers only depend on this class's
 * {@link #run} contract, not on how the process is actually launched.
 */
@Component
public class ProcessManager {

    private static final long DESTROY_GRACE_MS = 2000;

    public ProcessResult run(List<String> command, Path workingDir, Path stdinFile, long timeoutMs, long maxOutputBytes)
            throws IOException, InterruptedException {
        return run(command, workingDir, stdinFile, timeoutMs, maxOutputBytes, ProcessOutputListener.NONE);
    }

    /** {@code listener} is notified as the process starts and as stdout/stderr chunks arrive —
     * e.g. so a caller can stash the process handle for cancellation and/or stream output live. */
    public ProcessResult run(
            List<String> command,
            Path workingDir,
            Path stdinFile,
            long timeoutMs,
            long maxOutputBytes,
            ProcessOutputListener listener)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir.toFile());
        if (stdinFile != null) {
            builder.redirectInput(stdinFile.toFile());
        }

        long start = System.nanoTime();
        Process process = builder.start();
        listener.onStart(process);

        if (stdinFile == null) {
            // No input to feed — close stdin immediately so a program blocked on read() sees EOF
            // right away instead of hanging until the timeout kills it.
            process.getOutputStream().close();
        }

        StreamGobbler stdout = new StreamGobbler(process.getInputStream(), maxOutputBytes, listener::onStdout);
        StreamGobbler stderr = new StreamGobbler(process.getErrorStream(), maxOutputBytes, listener::onStderr);
        Thread stdoutThread = new Thread(stdout, "exec-stdout");
        Thread stderrThread = new Thread(stderr, "exec-stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        boolean timedOut = !finished;
        if (!finished) {
            process.destroy();
            if (!process.waitFor(DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(DESTROY_GRACE_MS, TimeUnit.MILLISECONDS);
            }
        }

        // Streams close once the process exits (naturally or via kill), so these joins are bounded.
        stdoutThread.join(DESTROY_GRACE_MS);
        stderrThread.join(DESTROY_GRACE_MS);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int exitCode = process.isAlive() ? -1 : process.exitValue();

        return new ProcessResult(
                stdout.text(), stderr.text(), stdout.truncated(), stderr.truncated(), exitCode, timedOut, durationMs);
    }
}
