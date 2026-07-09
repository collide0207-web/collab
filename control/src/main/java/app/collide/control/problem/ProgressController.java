package app.collide.control.problem;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.problem.ProgressService.UpdateProgressRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    /** All of the current user's progress rows (for merging into the sheet + dashboard). */
    @GetMapping
    public List<View> mine(@AuthenticationPrincipal AuthPrincipal me) {
        return progress.listForUser(me.id()).stream().map(View::of).toList();
    }

    @GetMapping("/{problemId}")
    public View get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID problemId) {
        return View.of(progress.getOrEmpty(me.id(), problemId));
    }

    /** Upsert progress (auto-save, status change, run bump…). */
    @PutMapping("/{problemId}")
    public View update(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID problemId,
                       @RequestBody UpdateProgressRequest req) {
        return View.of(progress.update(me.id(), problemId, req));
    }

    @PostMapping("/{problemId}/favorite")
    public View favorite(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID problemId) {
        return View.of(progress.setFavorite(me.id(), problemId, true));
    }

    @DeleteMapping("/{problemId}/favorite")
    public View unfavorite(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID problemId) {
        return View.of(progress.setFavorite(me.id(), problemId, false));
    }

    public record View(String problemId, String status, String language, JsonNode code,
                       boolean favorite, boolean completed, int timeSpent, int attemptCount,
                       int runCount, Instant lastOpened, Instant updatedAt) {
        static View of(UserProgress p) {
            return new View(p.getProblemId().toString(), p.getStatus(), p.getLanguage(), p.getCode(),
                    p.isFavorite(), p.isCompleted(), p.getTimeSpent(), p.getAttemptCount(),
                    p.getRunCount(), p.getLastOpened(), p.getUpdatedAt());
        }
    }
}
