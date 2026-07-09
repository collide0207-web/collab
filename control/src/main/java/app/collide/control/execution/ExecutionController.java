package app.collide.control.execution;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.execution.history.ExecutionHistory;
import app.collide.control.execution.model.ExecutionStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionController {

    private final ExecutionService executions;

    public ExecutionController(ExecutionService executions) {
        this.executions = executions;
    }

    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse execute(@AuthenticationPrincipal AuthPrincipal me, @RequestBody ExecutionRequest request) {
        UUID executionId = executions.submit(me.id(), request);
        return new SubmitResponse(executionId.toString(), ExecutionStatus.PENDING);
    }

    @GetMapping("/status/{executionId}")
    public StatusResponse status(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID executionId) {
        ExecutionState state = executions.getState(me.id(), executionId);
        return new StatusResponse(state.executionId().toString(), state.status());
    }

    @GetMapping("/result/{executionId}")
    public ExecutionResult result(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID executionId) {
        ExecutionState state = executions.getState(me.id(), executionId);
        ExecutionResult result = state.result();
        return result != null ? result : ExecutionResult.pending(state.executionId(), state.language());
    }

    @PostMapping("/cancel/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID executionId) {
        executions.cancel(me.id(), executionId);
    }

    @GetMapping("/history")
    public Page<HistoryEntryView> history(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return executions.listHistory(me.id(), language, status, pageable).map(HistoryEntryView::of);
    }

    public record SubmitResponse(String executionId, ExecutionStatus status) {}

    public record StatusResponse(String executionId, ExecutionStatus status) {}

    public record HistoryEntryView(
            String id,
            String language,
            String status,
            String sourceCode,
            String stdin,
            String stdout,
            String stderr,
            Integer exitCode,
            long executionTimeMs,
            String createdAt) {

        static HistoryEntryView of(ExecutionHistory h) {
            return new HistoryEntryView(
                    h.getId().toString(),
                    h.getLanguage(),
                    h.getStatus(),
                    h.getSourceCode(),
                    h.getStdin(),
                    h.getStdout(),
                    h.getStderr(),
                    h.getExitCode(),
                    h.getExecutionTimeMs(),
                    h.getCreatedAt().toString());
        }
    }
}
