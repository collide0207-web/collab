package app.collide.control.auth;

import java.util.UUID;

/** The authenticated user, derived from the verified JWT. */
public record AuthPrincipal(String userId, String name) {
    public UUID id() {
        return UUID.fromString(userId);
    }
}
