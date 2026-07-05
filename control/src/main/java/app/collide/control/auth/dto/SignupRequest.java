package app.collide.control.auth.dto;

import app.collide.control.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email + password signup. Validation runs before any DB work (fail fast). The
 * password rule lives in {@link StrongPassword}; username is 3-30 chars of a safe
 * alphabet (blocks whitespace/control chars that enable spoofing).
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,

        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$",
                message = "username must be 3-30 chars: letters, digits, dot, underscore or hyphen")
        String username,

        @NotBlank @Size(max = 100) String name,

        @StrongPassword String password) {}
