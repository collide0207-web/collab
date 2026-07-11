package app.collide.control.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

/**
 * DB-free unit tests for {@link ProblemSeeder#seed(JsonNode)}, covering the guard added around
 * orphan pruning: a seed file that parses to zero entries must never wipe the sheet's catalog.
 */
@ExtendWith(MockitoExtension.class)
class ProblemSeederTest {

    @Mock
    private ProblemRepository problems;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emptySeedArraySkipsPruningAndDeletesNothing() throws Exception {
        ProblemSeeder seeder = new ProblemSeeder(problems, mapper);
        JsonNode emptyRoot = mapper.readTree("[]");

        seeder.seed(emptyRoot);

        // No slugs were parsed, so the seeder must not even look up the sheet's existing rows,
        // and must never call deleteAll — that's the guard against wiping the whole catalog.
        verify(problems, never()).findBySheet(anyString());
        verify(problems, never()).deleteAll(any());
        verifyNoInteractions(problems); // nothing at all should touch the repository
    }

    @Test
    void nonEmptySeedStillPrunesOrphansOnTheSheet() throws Exception {
        Problem orphan = new Problem(UUID.randomUUID(), "house-robber");
        org.mockito.Mockito.when(problems.findBySlug("two-sum")).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(problems.findBySheet("leetcode150")).thenReturn(List.of(orphan));

        ProblemSeeder seeder = new ProblemSeeder(problems, mapper);
        JsonNode root = mapper.readTree(
                "[{\"slug\":\"two-sum\",\"title\":\"Two Sum\",\"difficulty\":\"Easy\",\"category\":\"Array\",\"sheet\":\"leetcode150\"}]");

        seeder.seed(root);

        verify(problems).findBySheet("leetcode150");
        verify(problems).deleteAll(List.of(orphan));
    }

    @Test
    void pruneItselfStillTreatsEmptySeededSetAsAllOrphans() {
        // Documents why the guard lives in the caller, not in prune(): prune() is a pure
        // function and correctly reports every row as an orphan when seededSlugs is empty.
        Problem existing = new Problem(UUID.randomUUID(), "two-sum");
        List<Problem> orphans = ProblemSeeder.prune(List.of(existing), java.util.Set.of());
        assertThat(orphans).containsExactly(existing);
    }

    /**
     * DB-free end-to-end deserialization check against the REAL seed resource (not a small
     * inline/fixture JSON like the tests above). Proves every entry in
     * {@code seed/leetcode150.json} parses through the exact same Jackson calls
     * {@link ProblemSeeder#seed(JsonNode)} uses — field extraction plus
     * {@code mapper.convertValue(harnessNode, ProblemHarness.class)} — without needing a
     * Postgres/testcontainers-backed Spring context.
     */
    @Test
    void realSeedResourceDeserializesEveryEntryCleanly() throws Exception {
        JsonNode root;
        try (var in = new ClassPathResource("seed/leetcode150.json").getInputStream()) {
            root = mapper.readTree(in);
        }

        assertThat(root.isArray()).as("seed/leetcode150.json must be a JSON array").isTrue();
        assertThat(root).hasSize(149);

        List<String> slugs = new java.util.ArrayList<>();
        for (JsonNode n : root) {
            // Mirror ProblemSeeder.seed(JsonNode) field-by-field so this test fails loudly
            // if any single entry can't be parsed the way the real seeder parses it.
            String slug = n.get("slug").asText();
            slugs.add(slug);
            String sheet = n.path("sheet").asText("leetcode150");
            String title = n.get("title").asText();
            String difficulty = n.get("difficulty").asText();
            String category = n.get("category").asText();
            List<String> tags = n.has("tags")
                    ? mapper.convertValue(n.get("tags"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
                    : List.of();
            String description = n.hasNonNull("description") ? n.get("description").asText() : null;
            List<Map<String, String>> examples = n.hasNonNull("examples")
                    ? mapper.convertValue(n.get("examples"),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {})
                    : null;
            String constraints = n.hasNonNull("constraints") ? n.get("constraints").asText() : null;
            String sourceUrl = n.hasNonNull("sourceUrl") ? n.get("sourceUrl").asText() : null;
            Map<String, String> starterCode = n.has("starterCode")
                    ? mapper.convertValue(n.get("starterCode"),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {})
                    : Map.of();
            List<String> supportedLanguages = n.has("supportedLanguages")
                    ? mapper.convertValue(n.get("supportedLanguages"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
                    : List.of();
            ProblemHarness harness = n.hasNonNull("harness")
                    ? mapper.convertValue(n.get("harness"), ProblemHarness.class)
                    : null;
            int order = n.path("order").asInt(0);

            assertThat(slug).as("slug must be non-blank").isNotBlank();
            assertThat(sheet).isEqualTo("leetcode150");
            assertThat(title).as("title for %s", slug).isNotBlank();
            assertThat(difficulty).as("difficulty for %s", slug).isNotBlank();
            assertThat(category).as("category for %s", slug).isNotBlank();
            assertThat(tags).as("tags for %s", slug).isNotNull();
            assertThat(starterCode).as("starterCode for %s", slug).isNotNull();
            assertThat(supportedLanguages).as("supportedLanguages for %s", slug).isNotNull();
            assertThat(harness).as("harness for %s", slug).isNotNull();
            assertThat(harness.tests()).as("harness.tests for %s", slug).isNotNull();
            // Silence unused-variable warnings for fields we only assert are parseable.
            assertThat(order).isGreaterThanOrEqualTo(0);
            org.assertj.core.api.Assertions.assertThatCode(() -> {
                if (description != null) description.length();
                if (examples != null) examples.size();
                if (constraints != null) constraints.length();
                if (sourceUrl != null) sourceUrl.length();
            }).doesNotThrowAnyException();
        }

        assertThat(slugs).as("every entry must have a slug parsed").hasSize(149);
        assertThat(new java.util.HashSet<>(slugs)).as("slugs must be unique").hasSize(149);
    }
}
