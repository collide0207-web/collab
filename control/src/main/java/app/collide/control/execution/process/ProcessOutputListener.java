package app.collide.control.execution.process;

/**
 * Observes one process run as it happens: the moment it starts (so a caller can stash the
 * handle for cancellation) and each stdout/stderr chunk as it's read (so a caller can stream
 * it live, e.g. over a WebSocket) — instead of only seeing the fully-aggregated
 * {@link ProcessResult} once the process finishes.
 */
public interface ProcessOutputListener {

    ProcessOutputListener NONE = new ProcessOutputListener() {};

    default void onStart(Process process) {}

    default void onStdout(String chunk) {}

    default void onStderr(String chunk) {}
}
