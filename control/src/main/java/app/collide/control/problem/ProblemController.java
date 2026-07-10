package app.collide.control.problem;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problems;

    public ProblemController(ProblemService problems) {
        this.problems = problems;
    }

    /** Browsable list (metadata only — search/filter/sort happen client-side for now). */
    @GetMapping
    public List<Summary> list(@RequestParam(defaultValue = "neetcode150") String sheet) {
        return problems.list(sheet).stream().map(Summary::of).toList();
    }

    @GetMapping("/categories")
    public List<String> categories(@RequestParam(defaultValue = "neetcode150") String sheet) {
        return problems.categories(sheet);
    }

    @GetMapping("/{slug}")
    public Detail get(@PathVariable String slug) {
        return Detail.of(problems.getBySlug(slug));
    }

    public record Summary(String id, String slug, String title, String difficulty,
                          String category, List<String> tags, int order, boolean hasStatement) {
        static Summary of(Problem p) {
            return new Summary(p.getId().toString(), p.getSlug(), p.getTitle(), p.getDifficulty(),
                    p.getCategory(), p.getTags(), p.getOrderIndex(),
                    p.getDescription() != null && !p.getDescription().isBlank());
        }
    }

    public record Detail(String id, String slug, String title, String difficulty, String category,
                         List<String> tags, String description, List<Map<String, String>> examples,
                         String constraints, String sourceUrl, Map<String, String> starterCode,
                         List<String> supportedLanguages, ProblemHarness harness) {
        static Detail of(Problem p) {
            return new Detail(p.getId().toString(), p.getSlug(), p.getTitle(), p.getDifficulty(),
                    p.getCategory(), p.getTags(), p.getDescription(), p.getExamples(), p.getConstraints(),
                    p.getSourceUrl(), p.getStarterCode(), p.getSupportedLanguages(), p.getHarness());
        }
    }
}
