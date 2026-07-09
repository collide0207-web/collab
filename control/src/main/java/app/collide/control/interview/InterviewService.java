package app.collide.control.interview;

import app.collide.control.common.ApiException;
import app.collide.control.room.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview question set + reference images for a room. Editing is gated on write
 * access (the interviewer = room owner/editor); reading requires membership. Image
 * bytes are fetched by id via a capability URL (see controller), so getImage() only
 * scopes by room and does no per-user check.
 */
@Service
public class InterviewService {

    private final InterviewQuestionsRepository questions;
    private final InterviewImageRepository images;
    private final RoomService rooms;

    public InterviewService(InterviewQuestionsRepository questions, InterviewImageRepository images, RoomService rooms) {
        this.questions = questions;
        this.images = images;
        this.rooms = rooms;
    }

    @Transactional
    public void save(UUID roomId, UUID actorId, JsonNode data) {
        requireWriter(roomId, actorId);
        if (data == null || !data.isArray()) {
            throw ApiException.badRequest("questions must be a JSON array");
        }
        InterviewQuestions row = questions.findById(roomId).orElse(null);
        if (row == null) {
            questions.save(new InterviewQuestions(roomId, data, actorId));
        } else {
            row.setData(data, actorId);
            questions.save(row);
        }
    }

    /** The room's question set, or null if none was set up. Requires membership. */
    public JsonNode get(UUID roomId, UUID actorId) {
        rooms.requireRole(roomId, actorId);
        return questions.findById(roomId).map(InterviewQuestions::getData).orElse(null);
    }

    @Transactional
    public UUID saveImage(UUID roomId, UUID actorId, String contentType, byte[] bytes) {
        requireWriter(roomId, actorId);
        UUID id = UUID.randomUUID();
        images.save(new InterviewImage(id, roomId, contentType, bytes));
        return id;
    }

    public InterviewImage getImage(UUID roomId, UUID imageId) {
        InterviewImage img = images.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("image not found"));
        if (!img.getRoomId().equals(roomId)) {
            throw ApiException.notFound("image not found");
        }
        return img;
    }

    private void requireWriter(UUID roomId, UUID actorId) {
        if (!rooms.requireRole(roomId, actorId).canWrite()) {
            throw ApiException.forbidden("only the interviewer can edit questions");
        }
    }
}
