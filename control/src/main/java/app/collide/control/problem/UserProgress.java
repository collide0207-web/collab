package app.collide.control.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One user's progress on one problem. Code is kept per-language ({ lang: source })
 * so switching languages preserves each language's work. Composite key mirrors the
 * room_members pattern.
 */
@Entity
@Table(name = "user_progress")
@IdClass(UserProgressId.class)
public class UserProgress {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "problem_id")
    private UUID problemId;

    @Column(nullable = false)
    private String status = "unsolved";

    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> code = Map.of();

    @Column(nullable = false)
    private boolean favorite = false;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "time_spent", nullable = false)
    private int timeSpent = 0;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "run_count", nullable = false)
    private int runCount = 0;

    @Column(name = "last_opened")
    private Instant lastOpened;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserProgress() {}

    public UserProgress(UUID userId, UUID problemId) {
        this.userId = userId;
        this.problemId = problemId;
    }

    public UUID getUserId() { return userId; }
    public UUID getProblemId() { return problemId; }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public Map<String, String> getCode() { return code; }
    public boolean isFavorite() { return favorite; }
    public boolean isCompleted() { return completed; }
    public int getTimeSpent() { return timeSpent; }
    public int getAttemptCount() { return attemptCount; }
    public int getRunCount() { return runCount; }
    public Instant getLastOpened() { return lastOpened; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setLanguage(String language) { this.language = language; }
    public void setCode(Map<String, String> code) { this.code = code; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public void setTimeSpent(int timeSpent) { this.timeSpent = timeSpent; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setRunCount(int runCount) { this.runCount = runCount; }
    public void setLastOpened(Instant lastOpened) { this.lastOpened = lastOpened; }
    public void touch() { this.updatedAt = Instant.now(); }
}
