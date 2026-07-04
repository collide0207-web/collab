package app.collide.control.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {
    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);
    List<RoomMember> findByRoomId(UUID roomId);
    List<RoomMember> findByUserId(UUID userId);
    long countByRoomIdAndRole(UUID roomId, String role);
}
