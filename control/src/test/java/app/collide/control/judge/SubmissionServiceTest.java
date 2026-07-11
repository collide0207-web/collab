package app.collide.control.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    private final UUID user = UUID.randomUUID();

    @Mock
    private SubmissionRepository repo;

    private final Map<UUID, Submission> store = new HashMap<>();

    @BeforeEach
    void backRepoWithMap() {
        lenient().when(repo.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            store.put(s.getId(), s);
            return s;
        });
        lenient().when(repo.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    }

    private SubmissionService serviceThatJudges(Verdict verdict) {
        return new SubmissionService(repo, Runnable::run, (slug, lang, src) -> verdict, Language::fromWire);
    }

    @Test
    void submitPersistsTerminalVerdictAfterJudging() {
        SubmissionService svc = serviceThatJudges(Verdict.accepted(100, 12));
        UUID id = svc.submit(user, "two-sum", "javascript", "function twoSum(){}");
        Submission s = svc.get(user, id);
        assertThat(s.getStatus()).isEqualTo("AC");
        assertThat(s.getPassed()).isEqualTo(100);
        assertThat(s.getTotal()).isEqualTo(100);
    }

    @Test
    void getRejectsOtherUsersSubmission() {
        SubmissionService svc = serviceThatJudges(Verdict.accepted(1, 1));
        UUID id = svc.submit(user, "two-sum", "javascript", "x");
        assertThatThrownBy(() -> svc.get(UUID.randomUUID(), id)).isInstanceOf(ApiException.class);
    }

    @Test
    void blankSourceIsRejected() {
        SubmissionService svc = serviceThatJudges(Verdict.accepted(1, 1));
        assertThatThrownBy(() -> svc.submit(user, "two-sum", "javascript", "  ")).isInstanceOf(ApiException.class);
    }

    @Test
    void judgeThrowingMarksSubmissionRuntimeError() {
        SubmissionService svc = new SubmissionService(repo, Runnable::run, (slug, lang, src) -> {
            throw new RuntimeException("boom");
        }, Language::fromWire);
        UUID id = svc.submit(user, "two-sum", "javascript", "x");
        assertThat(svc.get(user, id).getStatus()).isEqualTo("RE");
    }
}
