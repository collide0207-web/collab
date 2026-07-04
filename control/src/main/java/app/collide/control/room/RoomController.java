package app.collide.control.room;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.common.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public RoomView create(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateRoom req) {
        Room room = rooms.create(me.id(), req.name(), req.mode());
        return RoomView.of(room, Role.OWNER);
    }

    @GetMapping
    public List<RoomView> listMine(@AuthenticationPrincipal AuthPrincipal me) {
        return rooms.listMine(me.id()).stream()
                .map(r -> RoomView.of(r, rooms.requireRole(r.getId(), me.id())))
                .toList();
    }

    @GetMapping("/{roomId}")
    public RoomView get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID roomId) {
        Room room = rooms.get(roomId, me.id());
        return RoomView.of(room, rooms.requireRole(roomId, me.id()));
    }

    @GetMapping("/{roomId}/members")
    public List<MemberView> members(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID roomId) {
        return rooms.listMembers(roomId, me.id()).stream()
                .map(m -> new MemberView(m.getUserId().toString(), m.getRole().wire()))
                .toList();
    }

    @PatchMapping("/{roomId}/members/{userId}")
    public void changeRole(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdate req) {
        rooms.changeRole(roomId, me.id(), userId, Role.fromString(req.role()));
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID roomId,
            @PathVariable UUID userId) {
        rooms.removeMember(roomId, me.id(), userId);
    }

    public record CreateRoom(@NotBlank String name, String mode) {}
    public record RoleUpdate(@NotBlank String role) {}
    public record MemberView(String userId, String role) {}

    public record RoomView(String id, String name, String mode, String ownerId, String myRole) {
        static RoomView of(Room r, Role role) {
            return new RoomView(r.getId().toString(), r.getName(), r.getMode(), r.getOwnerId().toString(), role.wire());
        }
    }
}
