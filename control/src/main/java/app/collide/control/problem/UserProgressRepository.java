package app.collide.control.problem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, UserProgressId> {
    List<UserProgress> findByUserId(UUID userId);

    Optional<UserProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);
}
