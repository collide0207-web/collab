package app.collide.control.execution.history;

import app.collide.control.execution.ExecutionRequest;
import app.collide.control.execution.ExecutionResult;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Durable, paginated record of past executions. {@link #record} is called from the
 * execution worker thread right after a run finishes — never from the request thread — so
 * this write never adds latency to POST /execute or blocks on Postgres under load.
 */
@Service
public class ExecutionHistoryService {

    private final ExecutionHistoryRepository repository;

    public ExecutionHistoryService(ExecutionHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(UUID userId, ExecutionRequest request, ExecutionResult result) {
        ExecutionHistory entry = new ExecutionHistory(
                UUID.fromString(result.executionId()),
                userId,
                result.language(),
                request.sourceCode(),
                request.stdin(),
                result.stdout(),
                result.stderr(),
                result.exitCode(),
                result.status().name(),
                result.executionTimeMs());
        repository.save(entry);
    }

    public Page<ExecutionHistory> list(UUID userId, String language, String status, Pageable pageable) {
        Specification<ExecutionHistory> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (language != null && !language.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("language")), language.toUpperCase()));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
        }
        return repository.findAll(spec, pageable);
    }
}
