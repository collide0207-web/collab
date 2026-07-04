package app.collide.control.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    public AuthService.Result signup(@Valid @RequestBody SignupRequest req) {
        return auth.signup(req.email(), req.name(), req.password());
    }

    @PostMapping("/login")
    public AuthService.Result login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    /** Returns the current user (verifies the token is wired end-to-end). */
    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("userId", principal.userId(), "name", principal.name());
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank String name,
            @NotBlank @Size(min = 6, message = "password must be at least 6 characters") String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
}
