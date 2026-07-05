package app.collide.control.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Carries an opaque refresh token, used by both {@code /refresh} and {@code /logout}.
 * In a cookie-based deployment this would instead be read from an HttpOnly cookie.
 */
public record RefreshRequest(@NotBlank String refreshToken) {}
