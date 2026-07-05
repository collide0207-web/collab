package app.collide.control.auth.dto;

/**
 * The token bundle returned by signup/login/google/refresh. {@code accessToken} is a
 * short-lived JWT (verified by both this service and the Node sync server);
 * {@code refreshToken} is the opaque, rotating long-lived token. {@code expiresIn} is
 * the access-token lifetime in seconds, so the SPA knows when to refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
