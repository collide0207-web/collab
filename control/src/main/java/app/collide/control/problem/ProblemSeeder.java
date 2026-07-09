package app.collide.control.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds/updates the problem catalogue from a JSON resource on startup. Idempotent:
 * matches on slug, so re-runs update existing rows and add new ones without dupes.
 * The JSON file is the source of truth for problem content.
 */
@Component
public class ProblemSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProblemSeeder.class);
    private static final String RESOURCE = "seed/neetcode150.json";

    private final ProblemRepository problems;
    private final ObjectMapper mapper;

    public ProblemSeeder(ProblemRepository problems, ObjectMapper mapper) {
        this.problems = problems;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource res = new ClassPathResource(RESOURCE);
        if (!res.exists()) {
            log.warn("Problem seed {} not found — skipping", RESOURCE);
            return;
        }
        JsonNode root = mapper.readTree(res.getInputStream());
        if (!root.isArray()) {
            log.warn("Problem seed {} is not a JSON array — skipping", RESOURCE);
            return;
        }

        int idx = 0;
        for (JsonNode n : root) {
            String slug = n.get("slug").asText();
            Problem p = problems.findBySlug(slug).orElseGet(() -> new Problem(UUID.randomUUID(), slug));
            p.setSheet(n.path("sheet").asText("neetcode150"));
            p.setTitle(n.get("title").asText());
            p.setDifficulty(n.get("difficulty").asText());
            p.setCategory(n.get("category").asText());
            p.setTags(n.has("tags") ? n.get("tags") : mapper.createArrayNode());
            p.setDescription(n.hasNonNull("description") ? n.get("description").asText() : null);
            p.setExamples(n.has("examples") ? n.get("examples") : null);
            p.setConstraints(n.hasNonNull("constraints") ? n.get("constraints").asText() : null);
            p.setSourceUrl(n.hasNonNull("sourceUrl") ? n.get("sourceUrl").asText() : null);
            p.setStarterCode(n.has("starterCode") ? n.get("starterCode") : mapper.createObjectNode());
            p.setSupportedLanguages(n.has("supportedLanguages") ? n.get("supportedLanguages") : mapper.createArrayNode());
            p.setOrderIndex(n.path("order").asInt(idx));
            problems.save(p);
            idx++;
        }
        log.info("Seeded {} problems from {}", idx, RESOURCE);
    }
}
