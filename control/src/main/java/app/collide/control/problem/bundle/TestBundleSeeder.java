package app.collide.control.problem.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers generated test bundles from the committed {@code manifest.json} on startup, mirroring
 * {@link app.collide.control.problem.ProblemSeeder}: git (the manifest the {@code testgen} pipeline
 * writes) is the source of truth, the {@code problem_test_bundle} table is derived. Idempotent —
 * matches on {@code (problemSlug, version)}, so re-runs update existing rows and add new ones.
 *
 * <p>The bundle artifacts themselves are loaded lazily by the SP4 judge via {@link BundleStore};
 * this seeder only records their metadata and logs a warning if a referenced artifact is missing.
 */
@Component
public class TestBundleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestBundleSeeder.class);
    private static final String RESOURCE = "seed/test-bundles/manifest.json";

    private final ProblemTestBundleRepository bundles;
    private final BundleStore bundleStore;
    private final ObjectMapper mapper;

    public TestBundleSeeder(ProblemTestBundleRepository bundles, BundleStore bundleStore, ObjectMapper mapper) {
        this.bundles = bundles;
        this.bundleStore = bundleStore;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource res = new ClassPathResource(RESOURCE);
        if (!res.exists()) {
            log.warn("Test-bundle manifest {} not found — skipping bundle registration", RESOURCE);
            return;
        }
        JsonNode root = mapper.readTree(res.getInputStream());
        JsonNode entries = root.path("bundles");
        if (!entries.isArray()) {
            log.warn("Test-bundle manifest {} has no 'bundles' array — skipping", RESOURCE);
            return;
        }
        seed(entries);
    }

    /**
     * Upserts one registry row per manifest entry. Package-private so it can be exercised without a
     * Spring context / Postgres (mirrors {@code ProblemSeeder.seed}).
     */
    void seed(JsonNode entries) {
        int count = 0;
        Set<String> missingArtifacts = new HashSet<>();
        for (JsonNode e : entries) {
            String slug = e.get("problemSlug").asText();
            int version = e.get("version").asInt();
            ProblemTestBundle bundle = bundles
                    .findByProblemSlugAndVersion(slug, version)
                    .orElseGet(() -> new ProblemTestBundle(slug, version));
            bundle.setChecksum(e.get("checksum").asText());
            bundle.setCaseCount(e.get("caseCount").asInt());
            bundle.setStorageKey(e.get("storageKey").asText());
            bundle.setCheckerType(e.get("checkerType").asText());
            bundle.setTimeLimitMs(e.hasNonNull("timeLimitMs") ? e.get("timeLimitMs").asInt() : null);
            bundles.save(bundle);

            if (!bundleStore.exists(bundle.getStorageKey())) {
                missingArtifacts.add(bundle.getStorageKey());
            }
            count++;
        }
        if (!missingArtifacts.isEmpty()) {
            log.warn("Registered {} bundles but {} artifact(s) are missing from the bundle store: {}",
                    count, missingArtifacts.size(), missingArtifacts);
        }
        log.info("Registered {} test bundles from {}", count, RESOURCE);
    }
}
