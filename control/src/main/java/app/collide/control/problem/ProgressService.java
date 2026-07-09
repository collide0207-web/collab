package app.collide.control.problem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {

    private final UserProgressRepository progress;

    public ProgressService(UserProgressRepository progress) {
        this.progress = progress;
    }

    public List<UserProgress> listForUser(UUID userId) {
        return progress.findByUserId(userId);
    }

    public UserProgress getOrEmpty(UUID userId, UUID problemId) {
        return progress.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> new UserProgress(userId, problemId));
    }

    /** Upsert progress. Only non-null fields are applied (partial update). */
    @Transactional
    public UserProgress update(UUID userId, UUID problemId, UpdateProgressRequest req) {
        UserProgress p = progress.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> new UserProgress(userId, problemId));

        if (req.language() != null) p.setLanguage(req.language());
        if (req.code() != null) p.setCode(req.code());
        if (req.status() != null) p.setStatus(req.status());
        if (req.completed() != null) {
            p.setCompleted(req.completed());
            if (req.completed()) p.setStatus("solved");
        }
        if (Boolean.TRUE.equals(req.bumpRun())) {
            p.setRunCount(p.getRunCount() + 1);
            if ("unsolved".equals(p.getStatus())) p.setStatus("attempted");
        }
        if (Boolean.TRUE.equals(req.bumpAttempt())) {
            p.setAttemptCount(p.getAttemptCount() + 1);
        }
        if (req.timeSpentInc() != null && req.timeSpentInc() > 0) {
            p.setTimeSpent(p.getTimeSpent() + req.timeSpentInc());
        }
        p.setLastOpened(Instant.now());
        p.touch();
        return progress.save(p);
    }

    @Transactional
    public UserProgress setFavorite(UUID userId, UUID problemId, boolean favorite) {
        UserProgress p = progress.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> new UserProgress(userId, problemId));
        p.setFavorite(favorite);
        p.touch();
        return progress.save(p);
    }

    /** Partial-update payload — every field optional. */
    public record UpdateProgressRequest(
            String status,
            String language,
            Map<String, String> code,
            Boolean completed,
            Boolean bumpRun,
            Boolean bumpAttempt,
            Integer timeSpentInc) {}
}
