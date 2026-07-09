package app.collide.control.execution.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ProcessManager} directly against real OS commands — no Spring context,
 * no compiler/interpreter dependency, so this runs everywhere Java runs.
 */
class ProcessManagerTest {

    private final ProcessManager manager = new ProcessManager();

    @Test
    void capturesStdoutExitCodeAndDuration(@TempDir Path dir) throws Exception {
        List<String> command = OS.WINDOWS.isCurrentOs()
                ? List.of("cmd.exe", "/c", "echo hello")
                : List.of("/bin/sh", "-c", "echo hello");

        ProcessResult result = manager.run(command, dir, null, 5000, 1_000_000);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
        assertFalse(result.timedOut());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void feedsStdinFileToTheProcess(@TempDir Path dir) throws Exception {
        Path stdinFile = dir.resolve("stdin.txt");
        Files.writeString(stdinFile, "42\n");
        List<String> command;
        if (OS.WINDOWS.isCurrentOs()) {
            // `set /p` + a same-line `%line%` reference can't work in cmd.exe: the whole line is
            // expanded once before execution, before `set /p` has run. A two-line .bat avoids that.
            Path script = dir.resolve("echo_stdin.bat");
            Files.writeString(script, "@echo off\r\nset /p line=\r\necho %line%\r\n");
            command = List.of("cmd.exe", "/c", script.toString());
        } else {
            command = List.of("/bin/sh", "-c", "read line; echo $line");
        }

        ProcessResult result = manager.run(command, dir, stdinFile, 5000, 1_000_000);

        assertTrue(result.stdout().contains("42"), "expected stdin to be echoed back, got: " + result.stdout());
    }

    @Test
    void killsAProcessThatExceedsItsTimeout(@TempDir Path dir) throws Exception {
        // `timeout.exe` refuses to run at all with redirected stdin ("Input redirection is not
        // supported"), which would make it exit immediately instead of actually sleeping. `ping`
        // has no such restriction and is a standard way to sleep on Windows. Invoked directly
        // (no cmd.exe /c wrapper) so there's no extra console-host process that can keep a
        // lingering handle on the working directory after the forced kill, which otherwise
        // races @TempDir's own cleanup.
        List<String> command = OS.WINDOWS.isCurrentOs()
                ? List.of("ping", "-n", "11", "127.0.0.1")
                : List.of("/bin/sh", "-c", "sleep 10");

        ProcessResult result = manager.run(command, dir, null, 300, 1_000_000);

        assertTrue(result.timedOut(), "expected the long-running process to be marked as timed out");
        assertTrue(result.durationMs() < 5000, "should have been killed well before its natural 10s runtime");
    }

    @Test
    void truncatesOutputBeyondTheConfiguredCapWithoutDeadlocking(@TempDir Path dir) throws Exception {
        List<String> command = OS.WINDOWS.isCurrentOs()
                ? List.of("cmd.exe", "/c", "for /L %i in (1,1,5000) do @echo line%i")
                : List.of("/bin/sh", "-c", "for i in $(seq 1 5000); do echo line$i; done");

        ProcessResult result = manager.run(command, dir, null, 5000, 100);

        assertTrue(result.stdoutTruncated());
        assertTrue(result.stdout().length() <= 100);
        assertFalse(result.timedOut(), "the process must still be allowed to finish, not deadlock on a full pipe");
    }
}
