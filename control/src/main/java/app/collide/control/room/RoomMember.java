package app.collide.control.room;

import app.collide.control.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_members")
@IdClass(RoomMemberId.class)
public class RoomMember {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected RoomMember() {}

    public RoomMember(UUID roomId, UUID userId, Role role, UUID updatedBy) {
        this.roomId = roomId;
        this.userId = userId;
        this.role = role.wire();
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getRoomId() { return roomId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return Role.fromString(role); }

    public void setRole(Role role, UUID updatedBy) {
        this.role = role.wire();
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
