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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
