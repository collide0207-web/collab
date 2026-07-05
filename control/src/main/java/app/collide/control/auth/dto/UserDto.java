package app.collide.control.auth.dto;

import java.time.Instant;

/**
 * Public projection of a user. Deliberately omits password_hash, google_id,
 * failed_login_count, locked_until, version — nothing sensitive or internal leaves
 * the service. The entity is never serialized directly (mass-assignment / over-exposure
 * defence).
 */
public record UserDto(
        String id,
        String email,
        String username,
        String name,
        String role,
        boolean emailVerified,
        String profilePicture,
        String authProvider,
        Instant createdAt) {}
