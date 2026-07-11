package app.collide.control.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.execution.model.Language;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerTest {

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

    private AuthPrincipal principal(UUID id) {
        return new AuthPrincipal(id.toString(), "N", "e@x.com", "u", List.of("USER"));
    }

    private SubmissionController controllerAccepting() {
        SubmissionService svc = new SubmissionService(repo, Runnable::run,
                (slug, lang, src) -> Verdict.accepted(100, 5), Language::fromWire);
        return new SubmissionController(svc);
    }

    @Test
    void submitReturnsPendingWithId() {
        SubmissionController ctrl = controllerAccepting();
        var resp = ctrl.submit(principal(UUID.randomUUID()), "two-sum", new SubmitRequest("javascript", "function twoSum(){}"));
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(UUID.fromString(resp.submissionId())).isNotNull();
    }

    @Test
    void getReturnsTerminalVerdictViewForOwner() {
        UUID uid = UUID.randomUUID();
        SubmissionController ctrl = controllerAccepting();
        var resp = ctrl.submit(principal(uid), "two-sum", new SubmitRequest("javascript", "x"));
        SubmissionView view = ctrl.get(principal(uid), UUID.fromString(resp.submissionId()));
        assertThat(view.status()).isEqualTo("AC");
        assertThat(view.passed()).isEqualTo(100);
    }
}
