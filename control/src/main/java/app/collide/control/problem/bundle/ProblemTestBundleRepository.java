package app.collide.control.problem.bundle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemTestBundleRepository extends JpaRepository<ProblemTestBundle, Long> {

    Optional<ProblemTestBundle> findByProblemSlugAndVersion(String problemSlug, int version);

    List<ProblemTestBundle> findByProblemSlug(String problemSlug);
}
