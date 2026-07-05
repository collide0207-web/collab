package app.collide.control.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * "Continue with Google". The frontend obtains a Google ID token via Google Identity
 * Services and posts it here; the backend verifies it against Google's public keys.
 * We never trust any client-supplied profile fields — only the verified token.
 */
public record GoogleLoginRequest(@NotBlank String idToken) {}
