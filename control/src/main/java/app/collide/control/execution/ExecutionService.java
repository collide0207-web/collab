package app.collide.control.execution;

import app.collide.control.common.ApiException;
import app.collide.control.execution.executor.LanguageExecutor;
import app.collide.control.execution.executor.LanguageExecutorFactory;
import app.collide.control.execution.history.ExecutionHistory;
import app.collide.control.execution.history.ExecutionHistoryService;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessOutputListener;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.queue.ExecutionQueue;
import app.collide.control.execution.workspace.FileManager;
import app.collide.control.execution.workspace.Workspace;
import app.collide.control.execution.ws.ExecutionEvent;
import app.collide.control.execution.ws.ExecutionPublisher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one execution end to end: validate -> queue -> isolated workspace -> compile
 * (if the language needs it) -> run -> cleanup (always, via try-with-resources on
 * {@link Workspace}) -> record final state. Runs off the request thread via
 * {@link ExecutionQueue} — {@link #submit} returns as soon as the execution is queued;
 * {@link #getState} / {@link #cancel} are how callers observe/stop it afterwards, and
 * {@link ExecutionPublisher} is how they can watch its output live instead of polling.
 */
@Service
public class ExecutionService {

    private final FileManager fileManager;
    private final ProcessManager processManager;
    private final LanguageExecutorFactory executors;
    private final ExecutionQueue queue;
    private final ExecutionRegistry registry;
    private final ExecutionPublisher publisher;
    private final ExecutionHistoryService history;
    private final long timeoutMs;
    private final long maxSourceBytes;
    private final long maxInputBytes;
    private final long maxOutputBytes;

    public ExecutionService(
            FileManager fileManager,
            ProcessManager processManager,
            LanguageExecutorFactory executors,
            ExecutionQueue queue,
            ExecutionRegistry registry,
            ExecutionPublisher publisher,
            ExecutionHistoryService history,
            @Value("${collide.execution.timeout-seconds:10}") long timeoutSeconds,
            @Value("${collide.execution.max-source-bytes:65536}") long maxSourceBytes,
            @Value("${collide.execution.max-input-bytes:65536}") long maxInputBytes,
            @Value("${collide.execution.max-output-bytes:1048576}") long maxOutputBytes) {
        this.fileManager = fileManager;
        this.processManager = processManager;
        this.executors = executors;
        this.queue = queue;
        this.registry = registry;
        this.publisher = publisher;
        this.history = history;
        this.timeoutMs = timeoutSeconds * 1000;
        this.maxSourceBytes = maxSourceBytes;
        this.maxInputBytes = maxInputBytes;
        this.maxOutputBytes = maxOutputBytes;
    }

    /** Validates, registers, and queues the execution. Returns immediately with its id. */
    public UUID submit(UUID userId, ExecutionRequest request) {
        Language language = Language.fromWire(request.language());
        validate(request);

        UUID executionId = UUID.randomUUID();
        ExecutionState state = registry.create(executionId, userId, language);
        queue.submit(() -> runToCompletion(executionId, language, request, state));
        return executionId;
    }

    public ExecutionState getState(UUID userId, UUID executionId) {
        return requireOwnedBy(userId, registry.get(executionId));
    }

    public Page<ExecutionHistory> listHistory(UUID userId, String language, String status, Pageable pageable) {
        return history.list(userId, language, status, pageable);
    }

    public void cancel(UUID userId, UUID executionId) {
        requireOwnedBy(userId, registry.get(executionId)).requestCancellation();
    }

    private static ExecutionState requireOwnedBy(UUID userId, ExecutionState state) {
        if (!state.userId().equals(userId)) {
            throw ApiException.forbidden("not the owner of this execution");
        }
        return state;
    }

    private void runToCompletion(UUID executionId, Language language, ExecutionRequest request, ExecutionState state) {
        LanguageExecutor executor = executors.get(language);
        try (Workspace workspace = fileManager.create(executionId)) {
            fileManager.writeFile(workspace, executor.sourceFilename(), request.sourceCode());
            Path stdinFile = fileManager.writeStdinIfPresent(workspace, request.stdin());

            if (executor.requiresCompilation()) {
                state.markCompiling();
                publisher.publish(executionId, ExecutionEvent.status(executionId, state.status()));
                ProcessResult compileResult = executor.compile(workspace, processManager, timeoutMs, maxOutputBytes);
                if (state.isCancelRequested()) {
                    completeAndPublish(executionId, state, request, ExecutionResult.cancelled(executionId, language));
                    return;
                }
                if (compileResult.timedOut() || compileResult.exitCode() != 0) {
                    completeAndPublish(
                            executionId, state, request, ExecutionResult.compileFailure(executionId, language, compileResult));
                    return;
                }
            }

            state.markRunning();
            publisher.publish(executionId, ExecutionEvent.status(executionId, state.status()));

            ProcessOutputListener listener = new ProcessOutputListener() {
                @Override
                public void onStart(Process process) {
                    state.attachProcess(process);
                }

                @Override
                public void onStdout(String chunk) {
                    publisher.publish(executionId, ExecutionEvent.stdout(executionId, chunk));
                }

                @Override
                public void onStderr(String chunk) {
                    publisher.publish(executionId, ExecutionEvent.stderr(executionId, chunk));
                }
            };
            ProcessResult runResult = processManager.run(
                    executor.runCommand(workspace), workspace.root(), stdinFile, timeoutMs, maxOutputBytes, listener);

            ExecutionResult result = state.isCancelRequested()
                    ? ExecutionResult.cancelled(executionId, language)
                    : ExecutionResult.of(executionId, language, runResult);
            completeAndPublish(executionId, state, request, result);
        } catch (IOException e) {
            completeAndPublish(
                    executionId, state, request, ExecutionResult.error(executionId, language, "execution failed: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completeAndPublish(
                    executionId, state, request, ExecutionResult.error(executionId, language, "execution was interrupted"));
        }
    }

    private void completeAndPublish(UUID executionId, ExecutionState state, ExecutionRequest request, ExecutionResult result) {
        state.complete(result);
        publisher.publish(executionId, ExecutionEvent.result(executionId, result));
        history.record(state.userId(), request, result);
    }

    private void validate(ExecutionRequest request) {
        if (request.sourceCode() == null || request.sourceCode().isBlank()) {
            throw ApiException.badRequest("sourceCode must not be blank");
        }
        if (utf8Bytes(request.sourceCode()) > maxSourceBytes) {
            throw ApiException.badRequest("sourceCode exceeds the maximum size of " + maxSourceBytes + " bytes");
        }
        if (request.stdin() != null && utf8Bytes(request.stdin()) > maxInputBytes) {
            throw ApiException.badRequest("stdin exceeds the maximum size of " + maxInputBytes + " bytes");
        }
    }

    private static int utf8Bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
