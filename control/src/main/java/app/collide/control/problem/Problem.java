package app.collide.control.problem;

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
 * A coding-practice problem. Sheet-agnostic ({@code sheet} groups problems into a
 * set like "neetcode150"). Flexible fields are JSONB; statement text is nullable so
 * a row can seed as browsable metadata before an original statement is authored.
 */
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String sheet;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode tags;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode examples;

    @Column(columnDefinition = "text")
    private String constraints;

    @Column(name = "source_url")
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "starter_code", columnDefinition = "jsonb", nullable = false)
    private JsonNode starterCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_languages", columnDefinition = "jsonb", nullable = false)
    private JsonNode supportedLanguages;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Problem() {}

    public Problem(UUID id, String slug) {
        this.id = id;
        this.slug = slug;
    }

    public UUID getId() { return id; }
    public String getSheet() { return sheet; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDifficulty() { return difficulty; }
    public String getCategory() { return category; }
    public JsonNode getTags() { return tags; }
    public String getDescription() { return description; }
    public JsonNode getExamples() { return examples; }
    public String getConstraints() { return constraints; }
    public String getSourceUrl() { return sourceUrl; }
    public JsonNode getStarterCode() { return starterCode; }
    public JsonNode getSupportedLanguages() { return supportedLanguages; }
    public int getOrderIndex() { return orderIndex; }

    public void setSheet(String sheet) { this.sheet = sheet; }
    public void setTitle(String title) { this.title = title; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public void setCategory(String category) { this.category = category; }
    public void setTags(JsonNode tags) { this.tags = tags; }
    public void setDescription(String description) { this.description = description; }
    public void setExamples(JsonNode examples) { this.examples = examples; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setStarterCode(JsonNode starterCode) { this.starterCode = starterCode; }
    public void setSupportedLanguages(JsonNode supportedLanguages) { this.supportedLanguages = supportedLanguages; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
