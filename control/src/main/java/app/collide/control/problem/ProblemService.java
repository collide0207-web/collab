package app.collide.control.problem;

import app.collide.control.common.ApiException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProblemService {

    private final ProblemRepository problems;

    public ProblemService(ProblemRepository problems) {
        this.problems = problems;
    }

    /** All problems in a sheet, ordered by their curated position. */
    public List<Problem> list(String sheet) {
        return problems.findBySheet(sheet, Sort.by(Sort.Direction.ASC, "orderIndex"));
    }

    public Problem getBySlug(String slug) {
        return problems.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("problem not found"));
    }

    /** Distinct categories in a sheet (curated order preserved). */
    public List<String> categories(String sheet) {
        return list(sheet).stream().map(Problem::getCategory).distinct().toList();
    }
}
