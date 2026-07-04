package app.collide.control.link;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.common.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class LinkController {

    private final LinkService links;

    public LinkController(LinkService links) {
        this.links = links;
    }

    /** Owner creates a share link for a room with a given role. */
    @PostMapping("/rooms/{roomId}/links")
    public LinkService.Created create(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateLink req) {
        Instant expiresAt = req.expiresInHours() != null
                ? Instant.now().plusSeconds(req.expiresInHours() * 3600L)
                : null;
        return links.create(roomId, me.id(), Role.fromString(req.role()), expiresAt);
    }

    /** A logged-in user redeems a link token to join the room. */
    @PostMapping("/links/redeem")
    public LinkService.Redeemed redeem(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody RedeemLink req) {
        return links.redeem(req.token(), me.id());
    }

    @DeleteMapping("/links/{linkId}")
    public void revoke(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID linkId) {
        links.revoke(linkId, me.id());
    }

    public record CreateLink(@NotBlank String role, Integer expiresInHours) {}
    public record RedeemLink(@NotBlank String token) {}
}
