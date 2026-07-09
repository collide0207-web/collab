package app.collide.control.execution.queue;

/**
 * Runs execution work off the request thread. {@link InMemoryExecutionQueue} is the only
 * implementation today (bounded thread pool, single node); this interface is the seam where
 * a Redis/BullMQ-backed queue plugs in later — for horizontal scale across worker nodes —
 * without {@code ExecutionService} or the REST API changing, mirroring the
 * {@code PubSub}/{@code DocStore} interface+fallback pattern in the Node sync server.
 */
public interface ExecutionQueue {

    /** Throws if the queue is saturated — callers should surface that as 503/429, not block. */
    void submit(Runnable task);
}
