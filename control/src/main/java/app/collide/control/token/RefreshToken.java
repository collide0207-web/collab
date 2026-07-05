package app.collide.control.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A persisted refresh token. The raw token is NEVER stored — only its SHA-256 hash,
 * so a database leak cannot be replayed. Rotation records the successor id in
 * {@code replacedBy}; presenting an already-rotated (still-in-DB) token is treated as
 * theft and triggers revoke-all for the family. See {@code RefreshTokenService}.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    private String device;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt,
                        String device, String ipAddress, String userAgent) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.device = device;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    /** Usable = not revoked and not expired. */
    public boolean isActive() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public boolean isExpired() {
        return !expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revoked = true;
    }

    /** Mark this token as rotated into its successor. */
    public void replaceWith(UUID successorId) {
        this.revoked = true;
        this.replacedBy = successorId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public UUID getReplacedBy() { return replacedBy; }
    public String getDevice() { return device; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
