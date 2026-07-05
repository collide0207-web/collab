package app.collide.control.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial profile update. Both fields optional; only non-null fields are applied.
 * Deliberately narrow — email/username/role are NOT editable here (mass-assignment
 * defence: privileged fields can never be set through a profile update).
 */
public record UpdateProfileRequest(
        @Size(max = 100) String name,
        @Size(max = 2048) String profilePicture) {}
