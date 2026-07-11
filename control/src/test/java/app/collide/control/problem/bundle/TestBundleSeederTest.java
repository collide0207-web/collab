package app.collide.control.problem.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

/**
 * DB-free unit tests for {@link TestBundleSeeder#seed(JsonNode)} against the REAL committed
 * manifest + bundle artifacts produced by the {@code testgen} pipeline. Proves the manifest parses
 * exactly as the seeder reads it, that every referenced artifact exists in the {@link BundleStore}
 * and its recorded checksum matches the artifact's canonical content, and that upsert is
 * idempotent — all without a Postgres/testcontainers Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TestBundleSeederTest {

    @Mock
    private ProblemTestBundleRepository bundles;

    private final ObjectMapper mapper = new ObjectMapper();
    // The committed pilot bundles double as the local store root (matches the default property).
    private final BundleStore store = new LocalBundleStore(Path.of("src/main/resources/seed/test-bundles"));

    private JsonNode manifestEntries() throws Exception {
        try (var in = new ClassPathResource("seed/test-bundles/manifest.json").getInputStream()) {
            return mapper.readTree(in).path("bundles");
        }
    }

    @Test
    void registersEveryManifestEntryAndArtifactsExistWithMatchingChecksums() throws Exception {
        JsonNode entries = manifestEntries();
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).isNotEmpty();
        when(bundles.findByProblemSlugAndVersion(anyString(), anyInt())).thenReturn(Optional.empty());

        TestBundleSeeder seeder = new TestBundleSeeder(bundles, store, mapper);
        seeder.seed(entries);

        ArgumentCaptor<ProblemTestBundle> saved = ArgumentCaptor.forClass(ProblemTestBundle.class);
        verify(bundles, times(entries.size())).save(saved.capture());

        for (ProblemTestBundle b : saved.getAllValues()) {
            // Every registered bundle points at a real artifact...
            assertThat(store.exists(b.getStorageKey()))
                    .as("artifact for %s must exist", b.getProblemSlug())
                    .isTrue();
            // ...and the recorded checksum matches the artifact's canonical (uncompressed) content.
            String json = gunzip(store.load(b.getStorageKey()));
            assertThat(sha256(json))
                    .as("checksum for %s must match artifact", b.getProblemSlug())
                    .isEqualTo(b.getChecksum());
            assertThat(b.getCaseCount()).isGreaterThan(0);
            assertThat(b.getCheckerType()).isNotBlank();
        }

        // Spot-check the two-sum entry carries the unordered checker.
        assertThat(saved.getAllValues())
                .anySatisfy(b -> {
                    assertThat(b.getProblemSlug()).isEqualTo("two-sum");
                    assertThat(b.getCheckerType()).isEqualTo("unordered");
                });
    }

    @Test
    void reusesExistingRowOnRerun() throws Exception {
        JsonNode entries = manifestEntries();
        JsonNode first = entries.get(0);
        String slug = first.get("problemSlug").asText();
        int version = first.get("version").asInt();
        ProblemTestBundle existing = new ProblemTestBundle(slug, version);
        when(bundles.findByProblemSlugAndVersion(anyString(), anyInt())).thenReturn(Optional.empty());
        when(bundles.findByProblemSlugAndVersion(slug, version)).thenReturn(Optional.of(existing));

        new TestBundleSeeder(bundles, store, mapper).seed(entries);

        ArgumentCaptor<ProblemTestBundle> saved = ArgumentCaptor.forClass(ProblemTestBundle.class);
        verify(bundles, times(entries.size())).save(saved.capture());
        // The pre-existing row was updated in place (same instance), not duplicated.
        assertThat(saved.getAllValues()).contains(existing);
        assertThat(existing.getCaseCount()).isEqualTo(first.get("caseCount").asInt());
    }

    private static String gunzip(byte[] gz) throws Exception {
        try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String s) throws Exception {
        var md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Keep the List import used (documents the expected pilot size without over-asserting).
    @Test
    void manifestListsThePilotBundles() throws Exception {
        List<String> slugs = new java.util.ArrayList<>();
        manifestEntries().forEach(e -> slugs.add(e.get("problemSlug").asText()));
        assertThat(slugs).contains("two-sum", "majority-element", "merge-two-sorted-lists",
                "invert-binary-tree", "clone-graph", "min-stack", "powx-n");
    }
}
