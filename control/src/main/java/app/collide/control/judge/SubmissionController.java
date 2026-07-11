package app.collide.control.judge;

import app.collide.control.auth.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submit-tier REST surface: kick off authoritative judging, poll a submission, list a user's
 * history for a problem. {@code userId} is always taken from the verified JWT principal, never a
 * client field — the anti-tamper boundary (spec §5).
 */
@RestController
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping("/api/problems/{slug}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String slug,
            @RequestBody SubmitRequest request) {
        UUID id = submissions.submit(me.id(), slug, request.language(), request.sourceCode());
        return new SubmitResponse(id.toString(), "PENDING");
    }

    @GetMapping("/api/submissions/{id}")
    public SubmissionView get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        return SubmissionView.of(submissions.get(me.id(), id));
    }

    @GetMapping("/api/problems/{slug}/submissions")
    public List<SubmissionView> forProblem(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String slug) {
        return submissions.listForProblem(me.id(), slug).stream().map(SubmissionView::of).toList();
    }

    public record SubmitResponse(String submissionId, String status) {}
}
