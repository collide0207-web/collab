package app.collide.control.problem;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for {@link UserProgress}. */
public class UserProgressId implements Serializable {
    private UUID userId;
    private UUID problemId;

    public UserProgressId() {}

    public UserProgressId(UUID userId, UUID problemId) {
        this.userId = userId;
        this.problemId = problemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProgressId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(problemId, that.problemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, problemId);
    }
}
