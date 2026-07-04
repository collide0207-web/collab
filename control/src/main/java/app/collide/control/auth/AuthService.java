package app.collide.control.auth;

import app.collide.control.common.ApiException;
import app.collide.control.user.User;
import app.collide.control.user.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public Result signup(String email, String name, String password) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("email already registered");
        }
        User user = new User(UUID.randomUUID(), normalized, name.trim(), encoder.encode(password));
        users.save(user);
        return toResult(user);
    }

    public Result login(String email, String password) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("invalid credentials"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("invalid credentials");
        }
        return toResult(user);
    }

    private Result toResult(User user) {
        String token = jwt.issue(user.getId().toString(), user.getName());
        return new Result(user.getId().toString(), user.getEmail(), user.getName(), token);
    }

    public record Result(String id, String email, String name, String token) {}
}
