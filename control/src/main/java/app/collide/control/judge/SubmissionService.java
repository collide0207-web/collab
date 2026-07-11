package app.collide.control.judge;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns Submit lifecycle: validate, persist a PENDING row, judge off the request thread, then write
 * the terminal verdict back. {@code userId} always comes from the authenticated principal, never a
 * client field (anti-tamper boundary, spec §5).
 *
 * <p>The judging seam ({@link Judger}), the off-thread queue ({@code Consumer<Runnable>}), and the
 * language parser are constructor args so the service is unit-testable without the full
 * JudgeService/Spring graph or a database.
 */
public class SubmissionService {

    /** Seam so the service is unit-testable without the full JudgeService/Spring graph. */
    public interface Judger {
        Verdict judge(String slug, Language language, String sourceCode);
    }

    private final SubmissionRepository repo;
    private final Consumer<Runnable> queue;
    private final Judger judger;
    private final Function<String, Language> languageParser;

    public SubmissionService(SubmissionRepository repo, Consumer<Runnable> queue, Judger judger,
            Function<String, Language> languageParser) {
        this.repo = repo;
        this.queue = queue;
        this.judger = judger;
        this.languageParser = languageParser;
    }

    public UUID submit(UUID userId, String slug, String languageWire, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw ApiException.badRequest("sourceCode must not be blank");
        }
        Language language = languageParser.apply(languageWire);
        UUID id = UUID.randomUUID();
        Submission s = new Submission(id, userId, slug, language.name().toLowerCase(), sha256(sourceCode));
        s.setStatus("PENDING");
        repo.save(s);
        queue.accept(() -> runJudge(id, slug, language, sourceCode));
        return id;
    }

    private void runJudge(UUID id, String slug, Language language, String sourceCode) {
        Verdict v;
        try {
            v = judger.judge(slug, language, sourceCode);
        } catch (RuntimeException e) {
            markStatus(id, "RE");
            return;
        }
        Submission s = repo.findById(id).orElseThrow();
        s.setStatus(v.status().name());
        s.setVerdict(v.status().name());
        s.setPassed(v.passed());
        s.setTotal(v.total());
        s.setFailingCaseIndex(v.failingCaseIndex());
        s.setRuntimeMs(v.maxRuntimeMs());
        repo.save(s);
    }

    private void markStatus(UUID id, String status) {
        repo.findById(id).ifPresent(s -> {
            s.setStatus(status);
            s.setVerdict(status);
            repo.save(s);
        });
    }

    public Submission get(UUID userId, UUID id) {
        Submission s = repo.findById(id).orElseThrow(() -> ApiException.notFound("no such submission"));
        if (!s.getUserId().equals(userId)) {
            throw ApiException.forbidden("not the owner of this submission");
        }
        return s;
    }

    public List<Submission> listForProblem(UUID userId, String slug) {
        return repo.findByUserIdAndProblemSlugOrderByCreatedAtDesc(userId, slug);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
