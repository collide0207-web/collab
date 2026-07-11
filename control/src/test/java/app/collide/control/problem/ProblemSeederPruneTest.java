package app.collide.control.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSeederPruneTest {

    private Problem withSlug(String slug) {
        return new Problem(UUID.randomUUID(), slug);
    }

    @Test
    void pruneReturnsRowsWhoseSlugIsNotInTheActiveSeed() {
        Problem keep = withSlug("two-sum");
        Problem orphanA = withSlug("house-robber");   // NeetCode-only, not in LeetCode 150
        Problem orphanB = withSlug("koko-bananas");
        List<Problem> toDelete = ProblemSeeder.prune(
                List.of(keep, orphanA, orphanB), Set.of("two-sum"));
        assertThat(toDelete).containsExactlyInAnyOrder(orphanA, orphanB);
    }

    @Test
    void pruneReturnsEmptyWhenEverySlugIsSeeded() {
        Problem a = withSlug("two-sum");
        Problem b = withSlug("valid-parentheses");
        List<Problem> toDelete = ProblemSeeder.prune(
                List.of(a, b), Set.of("two-sum", "valid-parentheses"));
        assertThat(toDelete).isEmpty();
    }
}
