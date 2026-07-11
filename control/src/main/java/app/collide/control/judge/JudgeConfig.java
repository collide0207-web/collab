package app.collide.control.judge;

import app.collide.control.execution.executor.LanguageExecutorFactory;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.queue.ExecutionQueue;
import app.collide.control.execution.workspace.FileManager;
import app.collide.control.judge.driver.JudgeDriverGenerator;
import app.collide.control.problem.Problem;
import app.collide.control.problem.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the judge graph. {@link JudgeService} and {@link SubmissionService} are built here (not
 * component-scanned) because each takes a plain function seam — a slug→harness lookup and a
 * judge/queue delegate — that isn't itself a Spring bean, keeping both classes unit-testable
 * without a Spring context.
 */
@Configuration
public class JudgeConfig {

    @Bean
    public JudgeService judgeService(FileManager fm, ProcessManager pm, LanguageExecutorFactory executors,
            JudgeDriverGenerator gen, TestBundleLoader loader, ObjectMapper mapper, ProblemRepository problems) {
        return new JudgeService(fm, pm, executors, gen, loader, mapper,
                slug -> problems.findBySlug(slug).map(Problem::getHarness).orElse(null));
    }

    @Bean
    public SubmissionService submissionService(SubmissionRepository repo, ExecutionQueue queue, JudgeService judge) {
        return new SubmissionService(repo, queue::submit, judge::judge, Language::fromWire);
    }
}
