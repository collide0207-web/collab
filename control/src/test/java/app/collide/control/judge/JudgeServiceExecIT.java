package app.collide.control.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import app.collide.control.execution.executor.JavaExecutor;
import app.collide.control.execution.executor.LanguageExecutorFactory;
import app.collide.control.execution.executor.NodeExecutor;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.workspace.FileManager;
import app.collide.control.judge.Verdict.VerdictStatus;
import app.collide.control.judge.driver.JudgeDriverGenerator;
import app.collide.control.problem.ProblemHarness;
import app.collide.control.problem.bundle.LocalBundleStore;
import app.collide.control.problem.bundle.ProblemTestBundle;
import app.collide.control.problem.bundle.ProblemTestBundleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Runs REAL 100-case JS + Java submissions against the committed pilot bundles. */
@ExtendWith(MockitoExtension.class)
class JudgeServiceExecIT {

    @Mock
    private ProblemTestBundleRepository repo;

    private ProblemTestBundle bundleRow(String slug, String checkerType) {
        ProblemTestBundle b = new ProblemTestBundle(slug, 1);
        b.setStorageKey(slug + ".v1.json.gz");
        b.setCaseCount(100);
        b.setChecksum("chk-" + slug);
        b.setCheckerType(checkerType);
        b.setTimeLimitMs(2000);
        return b;
    }

    private JudgeService serviceFor(String slug, String checkerType, ProblemHarness harness) throws Exception {
        lenient().when(repo.findByProblemSlug(slug)).thenReturn(List.of(bundleRow(slug, checkerType)));
        var loader = new TestBundleLoader(repo, new LocalBundleStore(Path.of("src/main/resources/seed/test-bundles")), new ObjectMapper());
        var factory = new LanguageExecutorFactory(List.of(new NodeExecutor("node"), new JavaExecutor("javac", "java")));
        var fm = new FileManager(System.getProperty("java.io.tmpdir") + "/collide-judge-svc");
        return new JudgeService(fm, new ProcessManager(), factory, new JudgeDriverGenerator(), loader, new ObjectMapper(), s -> harness);
    }

    private ProblemHarness twoSum() {
        return new ProblemHarness("twoSum",
                List.of(new ProblemHarness.Param("nums", "int[]"), new ProblemHarness.Param("target", "int")),
                "int[]", List.of(), "unordered", 2000, null);
    }

    @Test
    void correctJsSolutionIsAccepted() throws Exception {
        JudgeService svc = serviceFor("two-sum", "unordered", twoSum());
        String user = "function twoSum(nums, target){ const m=new Map(); for(let i=0;i<nums.length;i++){ if(m.has(target-nums[i])) return [m.get(target-nums[i]), i]; m.set(nums[i], i);} return []; }";
        Verdict v = svc.judge("two-sum", Language.JAVASCRIPT, user);
        assertThat(v.status()).isEqualTo(VerdictStatus.AC);
        assertThat(v.passed()).isEqualTo(v.total()).isEqualTo(100);
    }

    @Test
    void wrongJsSolutionIsWrongAnswerWithFailingIndex() throws Exception {
        JudgeService svc = serviceFor("two-sum", "unordered", twoSum());
        String user = "function twoSum(nums, target){ return [0,0]; }";
        Verdict v = svc.judge("two-sum", Language.JAVASCRIPT, user);
        assertThat(v.status()).isEqualTo(VerdictStatus.WA);
        assertThat(v.failingCaseIndex()).isGreaterThanOrEqualTo(0);
        assertThat(v.passed()).isLessThan(v.total());
    }

    @Test
    void correctJavaSolutionIsAccepted() throws Exception {
        JudgeService svc = serviceFor("two-sum", "unordered", twoSum());
        String user = "class Solution { public int[] twoSum(int[] nums, int target){ java.util.Map<Integer,Integer> m=new java.util.HashMap<>(); for(int i=0;i<nums.length;i++){ if(m.containsKey(target-nums[i])) return new int[]{m.get(target-nums[i]), i}; m.put(nums[i], i);} return new int[]{}; } }";
        Verdict v = svc.judge("two-sum", Language.JAVA, user);
        assertThat(v.status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void javaCompileErrorIsCE() throws Exception {
        JudgeService svc = serviceFor("two-sum", "unordered", twoSum());
        Verdict v = svc.judge("two-sum", Language.JAVA, "class Solution { this does not compile }");
        assertThat(v.status()).isEqualTo(VerdictStatus.CE);
    }
}
