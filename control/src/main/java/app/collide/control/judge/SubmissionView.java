package app.collide.control.judge;

/** Client-facing submission shape. Never carries hidden inputs — only aggregate counts + index. */
public record SubmissionView(
        String submissionId, String problemSlug, String language, String status,
        int passed, int total, int failingCaseIndex, long runtimeMs, String createdAt) {

    static SubmissionView of(Submission s) {
        return new SubmissionView(
                s.getId().toString(), s.getProblemSlug(), s.getLanguage(), s.getStatus(),
                s.getPassed(), s.getTotal(), s.getFailingCaseIndex(), s.getRuntimeMs(),
                s.getCreatedAt().toString());
    }
}
