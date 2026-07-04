package app.collide.control.link;

import app.collide.control.common.ApiException;
import app.collide.control.common.Role;
import app.collide.control.room.RoomService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

    private final ShareLinkRepository links;
    private final RoomService rooms;

    public LinkService(ShareLinkRepository links, RoomService rooms) {
        this.links = links;
        this.rooms = rooms;
    }

    @Transactional
    public Created create(UUID roomId, UUID actorId, Role role, Instant expiresAt) {
        if (rooms.requireRole(roomId, actorId) != Role.OWNER) {
            throw ApiException.forbidden("only the owner can create share links");
        }
        if (role == Role.OWNER) {
            throw ApiException.badRequest("cannot create an owner link");
        }
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        ShareLink link = new ShareLink(UUID.randomUUID(), roomId, role, sha256(token), expiresAt, actorId);
        links.save(link);
        return new Created(link.getId().toString(), token, role.wire(), roomId.toString());
    }

    @Transactional
    public Redeemed redeem(String token, UUID userId) {
        ShareLink link = links.findByTokenHash(sha256(token))
                .orElseThrow(() -> ApiException.notFound("invalid link"));
        if (link.isRevoked()) throw ApiException.forbidden("link revoked");
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.forbidden("link expired");
        }
        rooms.addOrUpdateMember(link.getRoomId(), userId, link.getRole());
        return new Redeemed(link.getRoomId().toString(), link.getRole().wire());
    }

    @Transactional
    public void revoke(UUID linkId, UUID actorId) {
        ShareLink link = links.findById(linkId).orElseThrow(() -> ApiException.notFound("link not found"));
        if (rooms.requireRole(link.getRoomId(), actorId) != Role.OWNER) {
            throw ApiException.forbidden("only the owner can revoke links");
        }
        link.setRevoked(true);
        links.save(link);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Created(String id, String token, String role, String roomId) {}
    public record Redeemed(String roomId, String role) {}
}
