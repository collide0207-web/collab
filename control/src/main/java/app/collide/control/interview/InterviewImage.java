package app.collide.control.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A reference image attached to a room's interview questions, stored as binary. */
@Entity
@Table(name = "interview_images")
public class InterviewImage {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected InterviewImage() {}

    public InterviewImage(UUID id, UUID roomId, String contentType, byte[] bytes) {
        this.id = id;
        this.roomId = roomId;
        this.contentType = contentType;
        this.bytes = bytes;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
