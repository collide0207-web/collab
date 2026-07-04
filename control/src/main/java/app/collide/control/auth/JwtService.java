package app.collide.control.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies JWTs. Signs with HS256 using a shared secret that is
 * byte-identical to the Node sync server's JWT_SECRET, so tokens minted here verify
 * there (and vice-versa). We force HS256 explicitly — jjwt would otherwise pick a
 * stronger HMAC for a long key, which would not match the Node verifier.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(
            @Value("${collide.jwt.secret}") String secret,
            @Value("${collide.jwt.issuer}") String issuer,
            @Value("${collide.jwt.ttl-seconds}") long ttlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String userId, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("name", name)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AuthPrincipal verify(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Claims c = jws.getPayload();
        String name = c.get("name", String.class);
        return new AuthPrincipal(c.getSubject(), name != null ? name : "User");
    }
}
