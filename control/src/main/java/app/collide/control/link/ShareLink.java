package app.collide.control.link;

import app.collide.control.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_links")
public class ShareLink {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private String role;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ShareLink() {}

    public ShareLink(UUID id, UUID roomId, Role role, String tokenHash, Instant expiresAt, UUID createdBy) {
        this.id = id;
        this.roomId = roomId;
        this.role = role.wire();
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public Role getRole() { return Role.fromString(role); }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
