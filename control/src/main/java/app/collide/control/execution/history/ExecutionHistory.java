package app.collide.control.execution.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_history")
public class ExecutionHistory {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", columnDefinition = "text")
    private String sourceCode;

    @Column(columnDefinition = "text")
    private String stdin;

    @Column(columnDefinition = "text")
    private String stdout;

    @Column(columnDefinition = "text")
    private String stderr;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(nullable = false)
    private String status;

    @Column(name = "execution_time_ms", nullable = false)
    private long executionTimeMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ExecutionHistory() {}

    public ExecutionHistory(
            UUID id,
            UUID userId,
            String language,
            String sourceCode,
            String stdin,
            String stdout,
            String stderr,
            Integer exitCode,
            String status,
            long executionTimeMs) {
        this.id = id;
        this.userId = userId;
        this.language = language;
        this.sourceCode = sourceCode;
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.status = status;
        this.executionTimeMs = executionTimeMs;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getLanguage() { return language; }
    public String getSourceCode() { return sourceCode; }
    public String getStdin() { return stdin; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public Integer getExitCode() { return exitCode; }
    public String getStatus() { return status; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public Instant getCreatedAt() { return createdAt; }
}
