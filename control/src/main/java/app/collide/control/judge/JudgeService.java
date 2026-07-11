package app.collide.control.judge;

import app.collide.control.execution.executor.LanguageExecutor;
import app.collide.control.execution.executor.LanguageExecutorFactory;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.workspace.FileManager;
import app.collide.control.execution.workspace.Workspace;
import app.collide.control.judge.Verdict.VerdictStatus;
import app.collide.control.judge.checker.Checker;
import app.collide.control.judge.checker.Checkers;
import app.collide.control.judge.driver.JudgeDriverGenerator;
import app.collide.control.problem.ProblemHarness;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Function;

/**
 * The server-side judge: compiles the user's solution ONCE, then runs each hidden case as its own
 * process with a per-case wall-clock guard (so TLE/RE is attributable and one hang can't lose the
 * rest), applies the problem's checker, and aggregates a verdict — early-exiting on the first
 * non-AC case (LeetCode-style). Reuses the execution/ primitives; adds nothing to the Run path.
 *
 * <p>Built via {@code JudgeConfig} (not component-scanned) because it takes a plain
 * {@code Function<slug, harness>} lookup — kept as a constructor arg so the class is unit-testable
 * without a Spring context or a database.
 */
public class JudgeService {

    private static final long DEFAULT_TIME_LIMIT_MS = 2000;
    private static final long MAX_OUTPUT_BYTES = 1_000_000;
    private static final long COMPILE_TIMEOUT_MS = 20_000;

    private final FileManager fileManager;
    private final ProcessManager processManager;
    private final LanguageExecutorFactory executors;
    private final JudgeDriverGenerator driverGenerator;
    private final TestBundleLoader bundleLoader;
    private final ObjectMapper mapper;
    private final Function<String, ProblemHarness> harnessBySlug;

    public JudgeService(
            FileManager fileManager,
            ProcessManager processManager,
            LanguageExecutorFactory executors,
            JudgeDriverGenerator driverGenerator,
            TestBundleLoader bundleLoader,
            ObjectMapper mapper,
            Function<String, ProblemHarness> harnessBySlug) {
        this.fileManager = fileManager;
        this.processManager = processManager;
        this.executors = executors;
        this.driverGenerator = driverGenerator;
        this.bundleLoader = bundleLoader;
        this.mapper = mapper;
        this.harnessBySlug = harnessBySlug;
    }

    public Verdict judge(String slug, Language language, String userSource) {
        TestBundleLoader.LoadedBundle bundle = bundleLoader.load(slug);
        ProblemHarness harness = harnessBySlug.apply(slug);
        if (harness == null) {
            throw app.collide.control.common.ApiException.badRequest("problem has no harness: " + slug);
        }
        Checker checker = Checkers.parse(bundle.registry().getCheckerType(), mapper);
        long timeLimit = bundle.registry().getTimeLimitMs() != null ? bundle.registry().getTimeLimitMs() : DEFAULT_TIME_LIMIT_MS;
        List<TestCase> cases = bundle.cases();
        LanguageExecutor executor = executors.get(language);

        try (Workspace ws = fileManager.create(java.util.UUID.randomUUID())) {
            String program = driverGenerator.generate(language, harness, userSource);
            fileManager.writeFile(ws, executor.sourceFilename(), program);

            if (executor.requiresCompilation()) {
                ProcessResult compile = executor.compile(ws, processManager, COMPILE_TIMEOUT_MS, MAX_OUTPUT_BYTES);
                if (compile.timedOut() || compile.exitCode() != 0) {
                    return Verdict.compileError();
                }
            }
            List<String> runCmd = executor.runCommand(ws);

            int passed = 0;
            long maxRuntime = 0;
            for (int i = 0; i < cases.size(); i++) {
                TestCase c = cases.get(i);
                String stdinJson = mapper.writeValueAsString(c.input());
                var stdin = fileManager.writeFile(ws, "stdin.txt", stdinJson);
                ProcessResult r = processManager.run(runCmd, ws.root(), stdin, timeLimit, MAX_OUTPUT_BYTES);
                maxRuntime = Math.max(maxRuntime, r.durationMs());

                VerdictStatus caseStatus;
                if (r.timedOut()) {
                    caseStatus = VerdictStatus.TLE;
                } else if (r.exitCode() != 0) {
                    caseStatus = VerdictStatus.RE;
                } else if (!checker.check(r.stdout(), c.expected())) {
                    caseStatus = VerdictStatus.WA;
                } else {
                    passed++;
                    continue;
                }
                return Verdict.failed(caseStatus, passed, cases.size(), i, maxRuntime);
            }
            return Verdict.accepted(cases.size(), maxRuntime);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("judge interrupted", e);
        }
    }
}
