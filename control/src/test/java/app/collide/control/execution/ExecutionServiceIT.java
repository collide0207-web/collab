package app.collide.control.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.common.ApiException;
import app.collide.control.execution.executor.CppExecutor;
import app.collide.control.execution.executor.JavaExecutor;
import app.collide.control.execution.executor.LanguageExecutorFactory;
import app.collide.control.execution.executor.NodeExecutor;
import app.collide.control.execution.executor.PythonExecutor;
import app.collide.control.execution.history.ExecutionHistoryRepository;
import app.collide.control.execution.history.ExecutionHistoryService;
import app.collide.control.execution.model.ExecutionStatus;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.workspace.FileManager;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;

/**
 * End-to-end against the real OS toolchains — no mocks. Each test skips itself (rather than
 * failing the build) when its interpreter/compiler isn't on this machine's PATH, mirroring
 * the existing {@code @Testcontainers(disabledWithoutDocker = true)} skip pattern used for
 * Postgres integration tests when Docker isn't available.
 *
 * Uses a same-thread {@link app.collide.control.execution.queue.ExecutionQueue} (runs the
 * task immediately instead of on a worker pool) so {@code submit()} has already produced a
 * terminal result by the time it returns — no polling needed to test the core flow. The
 * {@link app.collide.control.execution.ws.ExecutionPublisher} is a no-op here since no
 * WebSocket is involved.
 */
class ExecutionServiceIT {

    @TempDir
    Path workspaceRoot;

    private final UUID userId = UUID.randomUUID();

    private ExecutionService service() throws IOException {
        FileManager fileManager = new FileManager(workspaceRoot.toString());
        ProcessManager processManager = new ProcessManager();
        LanguageExecutorFactory factory = new LanguageExecutorFactory(List.of(
                new PythonExecutor("python3"),
                new NodeExecutor("node"),
                new CppExecutor("g++"),
                new JavaExecutor("javac", "java")));
        return new ExecutionService(
                fileManager,
                processManager,
                factory,
                Runnable::run,
                new ExecutionRegistry(),
                (executionId, event) -> {},
                noOpHistoryService(),
                10,
                65536,
                65536,
                1_048_576);
    }

    /** No real Postgres in this unit test — a proxy stands in for the repository so
     * {@code record()}/{@code list()} have somewhere harmless to write/read. */
    private static ExecutionHistoryService noOpHistoryService() {
        ExecutionHistoryRepository fake = (ExecutionHistoryRepository) Proxy.newProxyInstance(
                ExecutionServiceIT.class.getClassLoader(),
                new Class<?>[] {ExecutionHistoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> args[0];
                    case "findAll" -> Page.empty();
                    default -> null;
                });
        return new ExecutionHistoryService(fake);
    }

    private ExecutionResult run(ExecutionService service, ExecutionRequest request) {
        UUID executionId = service.submit(userId, request);
        return service.getState(userId, executionId).result();
    }

    @Test
    void runsPythonWithCustomStdin() throws IOException {
        assumeCommandAvailable("python3");
        ExecutionResult result =
                run(service(), new ExecutionRequest("python", "name = input()\nprint(f'hello {name}')", "world"));

        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertTrue(result.stdout().contains("hello world"), result.stdout());
    }

    @Test
    void runsNodeAsyncCode() throws IOException {
        assumeCommandAvailable("node");
        String source = "Promise.resolve('hello from node').then(msg => console.log(msg));";
        ExecutionResult result = run(service(), new ExecutionRequest("javascript", source, null));

        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertTrue(result.stdout().contains("hello from node"), result.stdout());
    }

    @Test
    void runsJavaHelloWorld() throws IOException {
        assumeCommandAvailable("javac");
        String source = "public class Main {"
                + " public static void main(String[] args) { System.out.println(\"hello from java\"); }"
                + "}";
        ExecutionResult result = run(service(), new ExecutionRequest("java", source, null));

        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertTrue(result.stdout().contains("hello from java"), result.stdout());
    }

    @Test
    void runsCppHelloWorld() throws IOException {
        assumeCommandAvailable("g++");
        String source = "#include <iostream>\nint main() { std::cout << \"hello from cpp\"; return 0; }";
        ExecutionResult result = run(service(), new ExecutionRequest("cpp", source, null));

        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertTrue(result.stdout().contains("hello from cpp"), result.stdout());
    }

    @Test
    void reportsCppCompileErrorsWithoutRunning() throws IOException {
        assumeCommandAvailable("g++");
        ExecutionResult result = run(service(), new ExecutionRequest("cpp", "int main() { this is not cpp }", null));

        assertEquals(ExecutionStatus.FAILED, result.status());
        assertTrue(result.stderr() != null && !result.stderr().isBlank(), "expected compiler diagnostics in stderr");
    }

    @Test
    void cancelStopsALongRunningProgram() throws IOException, InterruptedException {
        assumeCommandAvailable("python3");
        // A real async queue (submit() must return before the run finishes) rather than the
        // same-thread one `service()` uses elsewhere in this file — cancellation only makes
        // sense against work that's still in flight on another thread.
        FileManager fileManager = new FileManager(workspaceRoot.toString());
        ProcessManager processManager = new ProcessManager();
        LanguageExecutorFactory factory = new LanguageExecutorFactory(List.of(new PythonExecutor("python3")));
        ExecutionService service = new ExecutionService(
                fileManager,
                processManager,
                factory,
                task -> new Thread(task).start(),
                new ExecutionRegistry(),
                (executionId, event) -> {},
                noOpHistoryService(),
                10,
                65536,
                65536,
                1_048_576);

        UUID executionId = service.submit(userId, new ExecutionRequest("python", "import time\ntime.sleep(30)", null));

        // Give the worker a moment to actually start the process, then cancel it.
        Thread.sleep(300);
        service.cancel(userId, executionId);
        Thread.sleep(500);

        ExecutionResult result = service.getState(userId, executionId).result();
        assertEquals(ExecutionStatus.CANCELLED, result.status());
    }

    @Test
    void rejectsBlankSourceCode() throws IOException {
        ApiException e = assertThrows(ApiException.class,
                () -> service().submit(userId, new ExecutionRequest("python", "   ", null)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void rejectsAnUnsupportedLanguage() throws IOException {
        assertThrows(ApiException.class, () -> service().submit(userId, new ExecutionRequest("ruby", "puts 1", null)));
    }

    @Test
    void rejectsAccessFromAUserWhoDidNotSubmitTheExecution() throws IOException {
        assumeCommandAvailable("node");
        ExecutionService service = service();
        UUID executionId = service.submit(userId, new ExecutionRequest("javascript", "console.log(1)", null));

        ApiException e = assertThrows(ApiException.class, () -> service.getState(UUID.randomUUID(), executionId));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.getStatus());
    }

    private static void assumeCommandAvailable(String command) {
        boolean available;
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            available = finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            available = false;
        }
        Assumptions.assumeTrue(available, command + " is not available on PATH in this environment — skipping");
    }
}
