package app.collide.control.judge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByUserIdAndProblemSlugOrderByCreatedAtDesc(UUID userId, String problemSlug);
}
