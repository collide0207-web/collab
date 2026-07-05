package app.collide.control.user;

import app.collide.control.auth.dto.UserDto;
import org.springframework.stereotype.Component;

/**
 * Entity -> DTO mapping. Kept as a tiny hand-written component (MapStruct would be
 * overkill for one type) so the "never expose entities" rule has a single choke point.
 */
@Component
public class UserMapper {

    public UserDto toDto(User u) {
        return new UserDto(
                u.getId().toString(),
                u.getEmail(),
                u.getUsername(),
                u.getName(),
                u.getRole().name(),
                u.isEmailVerified(),
                u.getProfilePicture(),
                u.getAuthProvider().name(),
                u.getCreatedAt());
    }
}
