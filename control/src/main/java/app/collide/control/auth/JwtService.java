package app.collide.control.auth;

import app.collide.control.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived ACCESS token. Signs with HS256 using a secret
 * byte-identical to the Node sync server's JWT_SECRET, so a token minted here also
 * authorises the WebSocket there.
 *
 * The sync server reads only `sub` (userId) and `name`; we keep those exactly and ADD
 * `email`, `username` and `roles` for the REST API. We force HS256 explicitly — jjwt
 * would otherwise pick a stronger HMAC for a long key, breaking the Node verifier.
 *
 * Refresh tokens are NOT JWTs — they're opaque and DB-backed (see RefreshTokenService).
 */
@Service
public class JwtService {

    /** Tolerated clock skew between this service and verifiers (spec edge case). */
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(
            @Value("${collide.jwt.secret}") String secret,
            @Value("${collide.jwt.issuer}") String issuer,
            @Value("${collide.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** Mint an access token for a user. Claims: sub, name, email, username, roles, iss, iat, exp. */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Verify signature + expiry and project the claims into an AuthPrincipal. Throws if invalid. */
    @SuppressWarnings("unchecked")
    public AuthPrincipal verify(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token);
        Claims c = jws.getPayload();
        String name = c.get("name", String.class);
        String email = c.get("email", String.class);
        String username = c.get("username", String.class);
        List<String> roles = c.get("roles", List.class);
        return new AuthPrincipal(
                c.getSubject(),
                name != null ? name : "User",
                email,
                username,
                roles != null ? List.copyOf(roles) : List.of());
    }
}
