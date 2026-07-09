package app.collide.control.execution.ws;

import java.util.UUID;

/**
 * Delivers live execution events to whoever is watching. {@link InProcessExecutionPublisher}
 * (in-process session map, single node) is the only implementation today — this interface is
 * the seam where a Redis-fan-out publisher plugs in later for horizontal scale, mirroring the
 * {@code PubSub} interface+fallback pattern in the Node sync server: cross-node delivery slots
 * in here without {@code ExecutionService} changing.
 */
public interface ExecutionPublisher {

    /** No-op if nothing is currently subscribed to this execution. */
    void publish(UUID executionId, ExecutionEvent event);
}
