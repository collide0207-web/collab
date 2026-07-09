package app.collide.control.execution.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.execution.ExecutionRequest;
import app.collide.control.execution.ExecutionResult;
import app.collide.control.execution.model.ExecutionStatus;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.support.PostgresIntegrationTest;
import app.collide.control.user.User;
import app.collide.control.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** Real Postgres (skips without Docker), since filtering/pagination is exactly the part of
 * {@link ExecutionHistoryService} that a fake repository can't meaningfully exercise. */
@SpringBootTest
class ExecutionHistoryServiceIT extends PostgresIntegrationTest {

    @Autowired
    ExecutionHistoryService service;

    @Autowired
    UserRepository users;

    private ExecutionResult completed(UUID executionId, Language language, String stdout) {
        return ExecutionResult.of(executionId, language, new ProcessResult(stdout, "", false, false, 0, false, 5));
    }

    /** execution_history.user_id has a real FK to users(id) — a row must exist first. */
    private UUID newUser() {
        UUID id = UUID.randomUUID();
        users.save(User.local(id, id + "@example.com", "user" + id.toString().substring(0, 8), "Test User", "hash"));
        return id;
    }

    @Test
    void recordsAndListsOnlyTheCallingUsersHistory() {
        UUID me = newUser();
        UUID someoneElse = newUser();

        service.record(me, new ExecutionRequest("python", "print(1)", null), completed(UUID.randomUUID(), Language.PYTHON, "1"));
        service.record(
                someoneElse, new ExecutionRequest("python", "print(2)", null), completed(UUID.randomUUID(), Language.PYTHON, "2"));

        Page<ExecutionHistory> mine = service.list(me, null, null, PageRequest.of(0, 20));

        assertEquals(1, mine.getTotalElements());
        assertEquals(me, mine.getContent().get(0).getUserId());
    }

    @Test
    void filtersByLanguageAndStatus() {
        UUID me = newUser();
        service.record(me, new ExecutionRequest("python", "print(1)", null), completed(UUID.randomUUID(), Language.PYTHON, "1"));
        service.record(me, new ExecutionRequest("javascript", "1", null), completed(UUID.randomUUID(), Language.JAVASCRIPT, "1"));

        Page<ExecutionHistory> pythonOnly = service.list(me, "python", null, PageRequest.of(0, 20));

        assertEquals(1, pythonOnly.getTotalElements());
        assertEquals("PYTHON", pythonOnly.getContent().get(0).getLanguage());

        Page<ExecutionHistory> completedOnly =
                service.list(me, null, ExecutionStatus.COMPLETED.name(), PageRequest.of(0, 20));
        assertTrue(completedOnly.getTotalElements() >= 2);
    }
}
