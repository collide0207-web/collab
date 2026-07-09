package app.collide.control.execution.history;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** {@link JpaSpecificationExecutor} gives the service optional language/status filters
 * (any combination, including neither) alongside pagination without a derived-query per
 * permutation. */
public interface ExecutionHistoryRepository
        extends JpaRepository<ExecutionHistory, UUID>, JpaSpecificationExecutor<ExecutionHistory> {
}
