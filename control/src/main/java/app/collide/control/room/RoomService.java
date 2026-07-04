package app.collide.control.room;

import app.collide.control.common.ApiException;
import app.collide.control.common.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final RoomMemberRepository members;

    public RoomService(RoomRepository rooms, RoomMemberRepository members) {
        this.rooms = rooms;
        this.members = members;
    }

    @Transactional
    public Room create(UUID ownerId, String name, String mode) {
        String m = "solo".equals(mode) ? "solo" : "group";
        Room room = new Room(UUID.randomUUID(), name, m, ownerId);
        rooms.save(room);
        members.save(new RoomMember(room.getId(), ownerId, Role.OWNER, ownerId));
        return room;
    }

    /** Resolve a member's role, or throw 403 if they aren't a member. */
    public Role requireRole(UUID roomId, UUID userId) {
        return members.findByRoomIdAndUserId(roomId, userId)
                .map(RoomMember::getRole)
                .orElseThrow(() -> ApiException.forbidden("not a member of this room"));
    }

    public Room get(UUID roomId, UUID userId) {
        requireRole(roomId, userId); // authorization
        return rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("room not found"));
    }

    public List<Room> listMine(UUID userId) {
        List<UUID> roomIds = members.findByUserId(userId).stream().map(RoomMember::getRoomId).toList();
        return rooms.findAllById(roomIds);
    }

    public List<RoomMember> listMembers(UUID roomId, UUID userId) {
        requireRole(roomId, userId);
        return members.findByRoomId(roomId);
    }

    @Transactional
    public void changeRole(UUID roomId, UUID actorId, UUID targetUserId, Role newRole) {
        requireOwner(roomId, actorId);
        RoomMember target = members.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("member not found"));
        // Guardrail: never demote the last owner.
        if (target.getRole() == Role.OWNER && newRole != Role.OWNER && ownerCount(roomId) <= 1) {
            throw ApiException.badRequest("room must have at least one owner");
        }
        target.setRole(newRole, actorId);
        members.save(target);
    }

    @Transactional
    public void removeMember(UUID roomId, UUID actorId, UUID targetUserId) {
        requireOwner(roomId, actorId);
        RoomMember target = members.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("member not found"));
        if (target.getRole() == Role.OWNER && ownerCount(roomId) <= 1) {
            throw ApiException.badRequest("room must have at least one owner");
        }
        members.delete(target);
    }

    /** Add or upgrade a member (used when redeeming a share link). */
    @Transactional
    public void addOrUpdateMember(UUID roomId, UUID userId, Role role) {
        RoomMember existing = members.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (existing == null) {
            members.save(new RoomMember(roomId, userId, role, userId));
        } else if (rank(role) > rank(existing.getRole())) {
            existing.setRole(role, userId); // only ever upgrade via a link
            members.save(existing);
        }
    }

    private void requireOwner(UUID roomId, UUID actorId) {
        if (requireRole(roomId, actorId) != Role.OWNER) {
            throw ApiException.forbidden("only the owner can manage members");
        }
    }

    private long ownerCount(UUID roomId) {
        return members.countByRoomIdAndRole(roomId, Role.OWNER.wire());
    }

    private static int rank(Role r) {
        return switch (r) {
            case OWNER -> 3;
            case EDITOR -> 2;
            case VIEWER -> 1;
        };
    }
}
