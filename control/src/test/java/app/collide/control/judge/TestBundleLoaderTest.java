package app.collide.control.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import app.collide.control.common.ApiException;
import app.collide.control.problem.bundle.BundleStore;
import app.collide.control.problem.bundle.LocalBundleStore;
import app.collide.control.problem.bundle.ProblemTestBundle;
import app.collide.control.problem.bundle.ProblemTestBundleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestBundleLoaderTest {

    private static final Path BUNDLE_DIR = Path.of("src/main/resources/seed/test-bundles");

    @Mock
    private ProblemTestBundleRepository repo;

    private TestBundleLoader loader() {
        BundleStore store = new LocalBundleStore(BUNDLE_DIR);
        return new TestBundleLoader(repo, store, new ObjectMapper());
    }

    private ProblemTestBundle row(String slug, int version, String key, int caseCount) {
        ProblemTestBundle b = new ProblemTestBundle(slug, version);
        b.setStorageKey(key);
        b.setCaseCount(caseCount);
        b.setChecksum("chk-" + slug + "-" + version);
        b.setCheckerType("exact");
        return b;
    }

    @Test
    void loadsAndParsesTheCommittedTwoSumBundle() {
        when(repo.findByProblemSlug("two-sum")).thenReturn(List.of(row("two-sum", 1, "two-sum.v1.json.gz", 100)));
        TestBundleLoader.LoadedBundle b = loader().load("two-sum");
        assertThat(b.cases()).hasSize(100);
        assertThat(b.cases().get(0).input()).hasSize(2); // nums, target
        assertThat(b.registry().getCheckerType()).isEqualTo("exact");
    }

    @Test
    void picksHighestVersionWhenMultipleRegistered() {
        when(repo.findByProblemSlug("two-sum")).thenReturn(List.of(
                row("two-sum", 1, "two-sum.v1.json.gz", 100),
                row("two-sum", 2, "two-sum.v1.json.gz", 100))); // reuse artifact; assert version picked
        assertThat(loader().load("two-sum").registry().getVersion()).isEqualTo(2);
    }

    @Test
    void throwsWhenNoBundleRegistered() {
        when(repo.findByProblemSlug("nope")).thenReturn(List.of());
        assertThatThrownBy(() -> loader().load("nope")).isInstanceOf(ApiException.class);
    }
}
