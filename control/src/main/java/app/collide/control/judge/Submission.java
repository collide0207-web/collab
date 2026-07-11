package app.collide.control.judge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One authoritative Submit record (SP4). Created PENDING, then updated with the terminal verdict
 * once the judge finishes. Hidden inputs are never stored — only the aggregate outcome and the
 * first failing case index. Durable counterpart of the in-flight judging, mirroring how
 * {@code execution_history} durably records the Run tier.
 */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_slug", nullable = false)
    private String problemSlug;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @Column(nullable = false)
    private String status;

    @Column
    private String verdict;

    @Column(nullable = false)
    private int passed;

    @Column(nullable = false)
    private int total;

    @Column(name = "failing_case_index", nullable = false)
    private int failingCaseIndex = -1;

    @Column(name = "runtime_ms", nullable = false)
    private long runtimeMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Submission() {}

    public Submission(UUID id, UUID userId, String problemSlug, String language, String sourceHash) {
        this.id = id;
        this.userId = userId;
        this.problemSlug = problemSlug;
        this.language = language;
        this.sourceHash = sourceHash;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProblemSlug() { return problemSlug; }
    public String getLanguage() { return language; }
    public String getSourceHash() { return sourceHash; }
    public String getStatus() { return status; }
    public String getVerdict() { return verdict; }
    public int getPassed() { return passed; }
    public int getTotal() { return total; }
    public int getFailingCaseIndex() { return failingCaseIndex; }
    public long getRuntimeMs() { return runtimeMs; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public void setPassed(int passed) { this.passed = passed; }
    public void setTotal(int total) { this.total = total; }
    public void setFailingCaseIndex(int failingCaseIndex) { this.failingCaseIndex = failingCaseIndex; }
    public void setRuntimeMs(long runtimeMs) { this.runtimeMs = runtimeMs; }
}
