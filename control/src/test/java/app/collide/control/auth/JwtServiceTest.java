package app.collide.control.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtService}. No Spring context — just the crypto/claims logic.
 * Locks in the sync-server contract (sub + name) and the API claims (email/username/roles),
 * plus tamper/wrong-secret rejection.
 */
class JwtServiceTest {

    private static final String SECRET = "collide_shared_dev_secret_change_in_production_0123456789";
    private final JwtService jwt = new JwtService(SECRET, "collide-test", 900);

    private User user() {
        return User.local(UUID.randomUUID(), "alice@example.com", "alice", "Alice", "hash");
    }

    @Test
    void issuesAndVerifiesRoundTrip() {
        User u = user();
        AuthPrincipal p = jwt.verify(jwt.issueAccessToken(u));

        assertEquals(u.getId().toString(), p.userId(), "sub must be the userId (sync contract)");
        assertEquals("Alice", p.name(), "name claim (sync contract)");
        assertEquals("alice@example.com", p.email());
        assertEquals("alice", p.username());
        assertTrue(p.roles().contains("USER"), "default role should be USER");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.issueAccessToken(user());
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertThrows(Exception.class, () -> jwt.verify(tampered));
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        String token = jwt.issueAccessToken(user());
        JwtService other = new JwtService(
                "a_totally_different_secret_that_is_also_long_enough_98765", "collide-test", 900);
        assertThrows(Exception.class, () -> other.verify(token));
    }

    @Test
    void toleratesSmallClockSkew() {
        // A token whose exp is "now" (already at the boundary) must still verify, because
        // we allow 60s of clock skew between services — the spec's clock-skew edge case.
        JwtService boundary = new JwtService(SECRET, "collide-test", 0);
        AuthPrincipal p = boundary.verify(boundary.issueAccessToken(user()));
        assertEquals("alice", p.username());
    }
}
