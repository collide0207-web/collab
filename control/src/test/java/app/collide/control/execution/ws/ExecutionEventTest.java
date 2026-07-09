package app.collide.control.execution.ws;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.execution.ExecutionResult;
import app.collide.control.execution.model.ExecutionStatus;
import app.collide.control.execution.model.Language;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionEventTest {

    private final UUID executionId = UUID.randomUUID();

    @Test
    void rendersAStdoutChunkAsJson() {
        String json = ExecutionEvent.stdout(executionId, "hello\nworld").toJson();

        assertTrue(json.contains("\"type\":\"stdout\""));
        assertTrue(json.contains("\"executionId\":\"" + executionId + "\""));
        assertTrue(json.contains("\"chunk\":\"hello\\nworld\""), json);
    }

    @Test
    void rendersAStatusEvent() {
        String json = ExecutionEvent.status(executionId, ExecutionStatus.RUNNING).toJson();

        assertTrue(json.contains("\"type\":\"status\""));
        assertTrue(json.contains("\"status\":\"RUNNING\""));
    }

    @Test
    void rendersATerminalResultEventWithNestedResult() {
        ExecutionResult result = ExecutionResult.of(
                executionId,
                Language.PYTHON,
                new app.collide.control.execution.process.ProcessResult("out", "err", false, false, 0, false, 42));

        String json = ExecutionEvent.result(executionId, result).toJson();

        assertTrue(json.contains("\"type\":\"result\""));
        assertTrue(json.contains("\"status\":\"COMPLETED\""));
        assertTrue(json.contains("\"result\":{"));
        assertTrue(json.contains("\"stdout\":\"out\""));
        assertTrue(json.contains("\"exitCode\":0"));
        assertTrue(json.contains("\"executionTimeMs\":42"));
    }

    @Test
    void quotesControlCharactersAndBackslashesSafely() {
        String json = ExecutionEvent.stderr(executionId, "line1\"\\\ttab").toJson();

        assertTrue(json.contains("\"chunk\":\"line1\\\"\\\\\\ttab\""), json);
    }
}
