package app.collide.control.problem.bundle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Registry row for one generated hidden-test bundle (SP3). A bundle is a gzipped
 * {@code {input, expected}[]} artifact produced by the {@code testgen} pipeline and stored via a
 * {@link BundleStore} under {@link #storageKey}. This row is metadata only — the cases live in
 * object storage / a mounted volume, not the database (master spec §5).
 *
 * <p>Uniquely identified by {@code (problemSlug, version)}. The SP4 judge resolves a problem's
 * active bundle by slug, loads the artifact by {@code storageKey}, and can use {@code checksum}
 * to invalidate a stale cache when a bundle is regenerated.
 */
@Entity
@Table(name = "problem_test_bundle")
public class ProblemTestBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_slug", nullable = false)
    private String problemSlug;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "case_count", nullable = false)
    private int caseCount;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** Per-case wall-clock limit (ms) the SP4 judge applies; nullable → judge default. */
    @Column(name = "time_limit_ms")
    private Integer timeLimitMs;

    @Column(name = "checker_type", nullable = false)
    private String checkerType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ProblemTestBundle() {}

    public ProblemTestBundle(String problemSlug, int version) {
        this.problemSlug = problemSlug;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public int getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(int caseCount) {
        this.caseCount = caseCount;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Integer getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(Integer timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public String getCheckerType() {
        return checkerType;
    }

    public void setCheckerType(String checkerType) {
        this.checkerType = checkerType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
