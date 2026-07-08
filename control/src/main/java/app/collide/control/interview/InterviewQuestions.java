package app.collide.control.interview;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The whole authored question set for one room, stored as a single JSONB blob so it
 * maps 1:1 to the shape the SPA sends. One row per room (room_id is the primary key).
 */
@Entity
@Table(name = "interview_questions")
public class InterviewQuestions {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private JsonNode data;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected InterviewQuestions() {}

    public InterviewQuestions(UUID roomId, JsonNode data, UUID updatedBy) {
        this.roomId = roomId;
        this.data = data;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data, UUID updatedBy) {
        this.data = data;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
