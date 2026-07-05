package app.collide.control.auth;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated user, derived solely from the verified JWT — never from
 * client-supplied fields. Carries the identity claims the API needs (id/name/email/
 * username) plus account roles for authorization.
 */
public record AuthPrincipal(
        String userId,
        String name,
        String email,
        String username,
        List<String> roles) {

    public UUID id() {
        return UUID.fromString(userId);
    }
}
