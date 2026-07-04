package app.collide.control.room;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for room_members (room_id, user_id). */
public class RoomMemberId implements Serializable {
    private UUID roomId;
    private UUID userId;

    public RoomMemberId() {}

    public RoomMemberId(UUID roomId, UUID userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomMemberId that)) return false;
        return Objects.equals(roomId, that.roomId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
