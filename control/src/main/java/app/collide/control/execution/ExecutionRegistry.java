package app.collide.control.execution;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory live registry of executions, for the fast status/cancel path. Single-node scale
 * only — same tradeoff as the Node sync server's default in-memory doc store — because the
 * durable, paginated record of past executions is {@code ExecutionHistoryService} (Postgres),
 * not this map.
 */
@Component
public class ExecutionRegistry {

    private final ConcurrentHashMap<UUID, ExecutionState> states = new ConcurrentHashMap<>();

    ExecutionState create(UUID executionId, UUID userId, Language language) {
        ExecutionState state = new ExecutionState(executionId, userId, language);
        states.put(executionId, state);
        return state;
    }

    public ExecutionState get(UUID executionId) {
        ExecutionState state = states.get(executionId);
        if (state == null) {
            throw ApiException.notFound("no such execution: " + executionId);
        }
        return state;
    }
}
