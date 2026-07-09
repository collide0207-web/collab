package app.collide.control.execution.model;

/** Lifecycle states for one execution. Terminal states are COMPLETED/FAILED/TIMEOUT/CANCELLED. */
public enum ExecutionStatus {
    PENDING,
    COMPILING,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }
}
