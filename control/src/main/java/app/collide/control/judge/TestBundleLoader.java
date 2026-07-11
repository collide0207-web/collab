package app.collide.control.judge;

import app.collide.control.common.ApiException;
import app.collide.control.problem.bundle.BundleStore;
import app.collide.control.problem.bundle.ProblemTestBundle;
import app.collide.control.problem.bundle.ProblemTestBundleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/**
 * Resolves and loads a problem's active hidden-test bundle for the judge. The active bundle is the
 * highest-version registry row for the slug; its gzipped artifact is loaded via {@link BundleStore},
 * decompressed, and parsed into {@link TestCase}s. Parsed cases are cached by checksum so repeated
 * submissions for the same problem version don't re-read/re-parse the artifact (spec §3).
 */
@Component
public class TestBundleLoader {

    private final ProblemTestBundleRepository registry;
    private final BundleStore store;
    private final ObjectMapper mapper;
    private final Map<String, List<TestCase>> cache = new ConcurrentHashMap<>();

    public TestBundleLoader(ProblemTestBundleRepository registry, BundleStore store, ObjectMapper mapper) {
        this.registry = registry;
        this.store = store;
        this.mapper = mapper;
    }

    public LoadedBundle load(String slug) {
        ProblemTestBundle row = registry.findByProblemSlug(slug).stream()
                .max(Comparator.comparingInt(ProblemTestBundle::getVersion))
                .orElseThrow(() -> ApiException.badRequest("no test bundle for " + slug));
        List<TestCase> cases = cache.computeIfAbsent(row.getChecksum(), k -> parse(row.getStorageKey()));
        return new LoadedBundle(row, cases);
    }

    private List<TestCase> parse(String storageKey) {
        byte[] gz = store.load(storageKey);
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return mapper.readValue(in.readAllBytes(), new TypeReference<List<TestCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse bundle " + storageKey, e);
        }
    }

    /** Active registry row + its parsed cases. */
    public record LoadedBundle(ProblemTestBundle registry, List<TestCase> cases) {}
}
