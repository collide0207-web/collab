package app.collide.control.auth;

import app.collide.control.common.ApiException;
import app.collide.control.common.ClientInfo;
import app.collide.control.common.Tokens;
import app.collide.control.token.RefreshToken;
import app.collide.control.token.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the opaque refresh-token lifecycle: issue, rotate-on-use, revoke, revoke-all.
 *
 * Security properties:
 *  - Only the SHA-256 hash is stored; the raw token is returned once and never persisted.
 *  - Every successful refresh ROTATES the token (old is revoked, new is minted).
 *  - Presenting an already-revoked token = reuse. Because a legit client discards a
 *    token the instant it rotates, a revoked token in the wild means it was stolen and
 *    replayed — so we revoke the user's entire token family (defence against theft).
 *  - The rotation path row-locks the token so concurrent refreshes can't both succeed.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repo;
    private final long ttlSeconds;

    public RefreshTokenService(
            RefreshTokenRepository repo,
            @Value("${collide.jwt.refresh-ttl-seconds}") long ttlSeconds) {
        this.repo = repo;
        this.ttlSeconds = ttlSeconds;
    }

    /** Mint a fresh refresh token for a user; returns the raw token (shown once). */
    @Transactional
    public String issue(UUID userId, ClientInfo client) {
        String raw = Tokens.generate();
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(), userId, Tokens.sha256(raw),
                Instant.now().plusSeconds(ttlSeconds),
                client.device(), client.ip(), client.userAgent());
        repo.save(token);
        return raw;
    }

    /** Result of a rotation: the owning user and the new raw refresh token. */
    public record Rotation(UUID userId, String rawToken) {}

    /**
     * Validate and rotate a refresh token. Throws 401 on invalid/expired/reused tokens.
     * On reuse, the whole family is revoked before throwing.
     */
    @Transactional
    public Rotation rotate(String rawToken, ClientInfo client) {
        String hash = Tokens.sha256(rawToken);
        RefreshToken current = repo.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> ApiException.unauthorized("invalid refresh token"));

        if (current.isRevoked()) {
            log.warn("Refresh token reuse detected for user {} — revoking all sessions", current.getUserId());
            repo.revokeAllForUser(current.getUserId());
            throw ApiException.unauthorized("refresh token reuse detected");
        }
        if (current.isExpired()) {
            throw ApiException.unauthorized("refresh token expired");
        }

        String raw = Tokens.generate();
        RefreshToken next = new RefreshToken(
                UUID.randomUUID(), current.getUserId(), Tokens.sha256(raw),
                Instant.now().plusSeconds(ttlSeconds),
                client.device(), client.ip(), client.userAgent());
        repo.save(next);
        current.replaceWith(next.getId());
        repo.save(current);
        return new Rotation(current.getUserId(), raw);
    }

    /** Revoke a single token (logout on this device). Idempotent — unknown token is a no-op. */
    @Transactional
    public void revoke(String rawToken) {
        repo.findByTokenHash(Tokens.sha256(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /** Revoke every live token for a user (logout everywhere / password change). */
    @Transactional
    public int revokeAll(UUID userId) {
        return repo.revokeAllForUser(userId);
    }
}
