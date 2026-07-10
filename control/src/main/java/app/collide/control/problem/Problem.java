package app.collide.control.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A coding-practice problem. Sheet-agnostic ({@code sheet} groups problems into a
 * set like "neetcode150"). Flexible fields are stored as JSONB but mapped to concrete
 * Java types (List/Map) — NOT Jackson JsonNode — so responses serialize as real JSON
 * arrays/objects. Statement text is nullable so a row can seed as browsable metadata.
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
    private List<String> tags;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, String>> examples;

    @Column(columnDefinition = "text")
    private String constraints;

    @Column(name = "source_url")
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "starter_code", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> starterCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_languages", columnDefinition = "jsonb", nullable = false)
    private List<String> supportedLanguages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ProblemHarness harness;

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
    public List<String> getTags() { return tags; }
    public String getDescription() { return description; }
    public List<Map<String, String>> getExamples() { return examples; }
    public String getConstraints() { return constraints; }
    public String getSourceUrl() { return sourceUrl; }
    public Map<String, String> getStarterCode() { return starterCode; }
    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public ProblemHarness getHarness() { return harness; }
    public int getOrderIndex() { return orderIndex; }

    public void setSheet(String sheet) { this.sheet = sheet; }
    public void setTitle(String title) { this.title = title; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public void setCategory(String category) { this.category = category; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setDescription(String description) { this.description = description; }
    public void setExamples(List<Map<String, String>> examples) { this.examples = examples; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setStarterCode(Map<String, String> starterCode) { this.starterCode = starterCode; }
    public void setSupportedLanguages(List<String> supportedLanguages) { this.supportedLanguages = supportedLanguages; }
    public void setHarness(ProblemHarness harness) { this.harness = harness; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
