package app.collide.control.interview;

import app.collide.control.auth.AuthPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import app.collide.control.common.ApiException;

@RestController
@RequestMapping("/rooms/{roomId}/interview")
public class InterviewController {

    private final InterviewService interview;

    public InterviewController(InterviewService interview) {
        this.interview = interview;
    }

    /** Save/replace the whole question set (interviewer only). Echoes it back. */
    @PutMapping
    public JsonNode save(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID roomId, @RequestBody JsonNode questions) {
        interview.save(roomId, me.id(), questions);
        return questions;
    }

    /** Fetch the question set (any room member). Empty array when none set up. */
    @GetMapping
    public JsonNode get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID roomId) {
        JsonNode data = interview.get(roomId, me.id());
        return data != null ? data : JsonNodeFactory.instance.arrayNode();
    }

    /** Upload a reference image (interviewer only). Returns its id for building the URL. */
    @PostMapping("/images")
    public ImageRef upload(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID roomId,
                           @RequestParam("file") MultipartFile file) throws IOException {
        String type = file.getContentType();
        if (file.isEmpty() || type == null || !type.startsWith("image/")) {
            throw ApiException.badRequest("file must be an image");
        }
        UUID id = interview.saveImage(roomId, me.id(), type, file.getBytes());
        return new ImageRef(id.toString());
    }

    /**
     * Serve image bytes. Public (permitAll in SecurityConfig) so an <img> tag can load
     * it without an Authorization header — the random UUID acts as the capability.
     */
    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable UUID roomId, @PathVariable UUID imageId) {
        InterviewImage img = interview.getImage(roomId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(img.getBytes());
    }

    public record ImageRef(String id) {}
}
