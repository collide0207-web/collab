package app.collide.control.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests that need a real Postgres. Spins up a throwaway container
 * (Flyway runs V1+V2 against it) and points the datasource at it via dynamic properties.
 * {@code disabledWithoutDocker = true} means these tests SKIP cleanly on machines/CI
 * without a Docker daemon instead of failing.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Google login disabled in tests (no client id) — the /google path returns 503.
        registry.add("collide.google.client-id", () -> "");
    }
}
