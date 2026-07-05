package app.collide.control;

import app.collide.control.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Full application context loads against a real Postgres (skipped without Docker). */
@SpringBootTest
class ControlApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
    }
}
