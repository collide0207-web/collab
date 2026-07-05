package app.collide.control.auth.dto;

import app.collide.control.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Change password while authenticated. Requires the current password (re-auth). */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @StrongPassword String newPassword) {}
