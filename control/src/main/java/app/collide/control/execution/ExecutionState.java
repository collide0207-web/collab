package app.collide.control.execution;

import app.collide.control.execution.model.ExecutionStatus;
import app.collide.control.execution.model.Language;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Live status of one execution, shared between the worker thread running it and the request
 * threads polling/cancelling it. Only the worker thread ever advances {@code status}/{@code
 * result}; {@link #requestCancellation()} is the one thing another thread calls, so the
 * synchronization need is minimal — plain {@code volatile} fields, no locks.
 */
public final class ExecutionState {

    private static final long FORCE_KILL_GRACE_MS = 1500;

    private final UUID executionId;
    private final UUID userId;
    private final Language language;
    private volatile ExecutionStatus status = ExecutionStatus.PENDING;
    private volatile ExecutionResult result;
    private volatile Process process;
    private volatile boolean cancelRequested = false;

    ExecutionState(UUID executionId, UUID userId, Language language) {
        this.executionId = executionId;
        this.userId = userId;
        this.language = language;
    }

    public UUID executionId() {
        return executionId;
    }

    /** The user who submitted this execution — the only user allowed to view/cancel it. */
    public UUID userId() {
        return userId;
    }

    public Language language() {
        return language;
    }

    public ExecutionStatus status() {
        return status;
    }

    public ExecutionResult result() {
        return result;
    }

    void markCompiling() {
        status = ExecutionStatus.COMPILING;
    }

    void markRunning() {
        status = ExecutionStatus.RUNNING;
    }

    void complete(ExecutionResult result) {
        this.result = result;
        this.status = result.status();
    }

    /** Passed as the process-manager's onStart hook so a later cancel() has something to kill. */
    void attachProcess(Process process) {
        this.process = process;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /**
     * Best-effort: flags the run so the worker reports CANCELLED at its next checkpoint, and
     * kills the attached process (if the run phase has started) with a grace period before
     * escalating to a forceful kill. If cancellation arrives during compilation, the flag is
     * still honored right after compilation finishes — the (typically sub-second) compiler
     * process itself isn't interrupted mid-flight in this pass.
     */
    public void requestCancellation() {
        cancelRequested = true;
        Process p = process;
        if (p != null && p.isAlive()) {
            p.destroy();
            CompletableFuture.delayedExecutor(FORCE_KILL_GRACE_MS, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        if (p.isAlive()) {
                            p.destroyForcibly();
                        }
                    });
        }
    }
}
