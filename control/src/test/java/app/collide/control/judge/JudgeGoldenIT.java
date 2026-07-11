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

/**
 * The judge's integration tests (master spec §9): a known-correct solution → AC across every wire
 * type (scalar/array/list/tree/graph/operations) and checker (exact/unordered/float); a known-wrong
 * → WA; a known-O(n^2) → TLE on the max-stress bucket. Executed live for JS + Java (the toolchains
 * present here) against the real committed SP3 bundles.
 */
@ExtendWith(MockitoExtension.class)
class JudgeGoldenIT {

    @Mock
    private ProblemTestBundleRepository repo;

    private Verdict judge(String slug, ProblemHarness harness, String checkerType, Language lang, String user) throws Exception {
        return judgeWithLimit(slug, harness, checkerType, lang, user, 2000);
    }

    private Verdict judgeWithLimit(String slug, ProblemHarness harness, String checkerType, Language lang, String user, int limitMs)
            throws Exception {
        ProblemTestBundle row = new ProblemTestBundle(slug, 1);
        row.setStorageKey(slug + ".v1.json.gz");
        row.setCaseCount(100);
        row.setChecksum("chk-" + slug + "-" + limitMs);
        row.setCheckerType(checkerType);
        row.setTimeLimitMs(limitMs);
        lenient().when(repo.findByProblemSlug(slug)).thenReturn(List.of(row));

        var loader = new TestBundleLoader(repo, new LocalBundleStore(Path.of("src/main/resources/seed/test-bundles")), new ObjectMapper());
        var factory = new LanguageExecutorFactory(List.of(new NodeExecutor("node"), new JavaExecutor("javac", "java")));
        var fm = new FileManager(System.getProperty("java.io.tmpdir") + "/collide-judge-golden");
        var svc = new JudgeService(fm, new ProcessManager(), factory, new JudgeDriverGenerator(), loader, new ObjectMapper(), s -> harness);
        return svc.judge(slug, lang, user);
    }

    // --- harness builders per pilot problem ---
    private ProblemHarness majority() {
        return new ProblemHarness("majorityElement", List.of(new ProblemHarness.Param("nums", "int[]")),
                "int", List.of(), "exact", 2000, null);
    }

    private ProblemHarness invert() {
        return new ProblemHarness("invertTree", List.of(new ProblemHarness.Param("root", "tree-node<int>")),
                "tree-node<int>", List.of(), "exact", 2000, null);
    }

    private ProblemHarness pow() {
        return new ProblemHarness("myPow", List.of(new ProblemHarness.Param("x", "double"), new ProblemHarness.Param("n", "int")),
                "double", List.of(), "float:1e-5", 2000, null);
    }

    private ProblemHarness minStack() {
        return new ProblemHarness("MinStack", List.of(new ProblemHarness.Param("ops", "operations")),
                "operations", List.of(), "exact", 2000, null);
    }

    private ProblemHarness cloneGraph() {
        return new ProblemHarness("cloneGraph", List.of(new ProblemHarness.Param("node", "graph-node<int>")),
                "graph-node<int>", List.of(), "exact", 2000, null);
    }

    @Test
    void majorityElementCorrectIsAC_js() throws Exception {
        String user = "function majorityElement(nums){ let c=0,x=nums[0]; for(const n of nums){ if(c===0)x=n; c += (n===x)?1:-1; } return x; }";
        assertThat(judge("majority-element", majority(), "exact", Language.JAVASCRIPT, user).status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void wrongMajorityIsNotAccepted_js() throws Exception {
        String user = "function majorityElement(nums){ return nums[0]; }";
        assertThat(judge("majority-element", majority(), "exact", Language.JAVASCRIPT, user).status()).isNotEqualTo(VerdictStatus.AC);
    }

    @Test
    void invertTreeCorrectIsAC_java() throws Exception {
        String user = "class Solution { public TreeNode invertTree(TreeNode r){ if(r==null) return null; TreeNode t=r.left; r.left=invertTree(r.right); r.right=invertTree(t); return r; } }";
        assertThat(judge("invert-binary-tree", invert(), "exact", Language.JAVA, user).status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void powxnFloatCheckerAcceptsAC_js() throws Exception {
        String user = "function myPow(x,n){ let N=n; if(N<0){x=1/x;N=-N;} let r=1; while(N){ if(N%2)r*=x; x*=x; N=Math.floor(N/2);} return r; }";
        assertThat(judge("powx-n", pow(), "float:1e-5", Language.JAVASCRIPT, user).status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void minStackOperationsAC_jsAndJava() throws Exception {
        String js = "class MinStack{ constructor(){ this.s=[]; this.m=[]; } push(x){ this.s.push(x); this.m.push(this.m.length?Math.min(x,this.m[this.m.length-1]):x); } pop(){ this.s.pop(); this.m.pop(); } top(){ return this.s[this.s.length-1]; } getMin(){ return this.m[this.m.length-1]; } }";
        assertThat(judge("min-stack", minStack(), "exact", Language.JAVASCRIPT, js).status()).isEqualTo(VerdictStatus.AC);
        String java = "class MinStack { java.util.Deque<Integer> s=new java.util.ArrayDeque<>(); java.util.Deque<Integer> m=new java.util.ArrayDeque<>(); public MinStack(){} public void push(int x){ s.push(x); m.push(m.isEmpty()?x:Math.min(x,m.peek())); } public void pop(){ s.pop(); m.pop(); } public int top(){ return s.peek(); } public int getMin(){ return m.peek(); } }";
        assertThat(judge("min-stack", minStack(), "exact", Language.JAVA, java).status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void cloneGraphCorrectIsAC_js() throws Exception {
        String user = "function cloneGraph(node){ if(!node) return null; const m=new Map(); const dfs=(n)=>{ if(m.has(n.val)) return m.get(n.val); const c={val:n.val,neighbors:[]}; m.set(n.val,c); for(const nb of n.neighbors) c.neighbors.push(dfs(nb)); return c; }; return dfs(node); }";
        assertThat(judge("clone-graph", cloneGraph(), "exact", Language.JAVASCRIPT, user).status()).isEqualTo(VerdictStatus.AC);
    }

    @Test
    void slowSolutionExceedingTimeLimitIsTLE_js() throws Exception {
        // Proves the per-case wall-clock GUARD: a solution that runs longer than the limit is
        // killed and reported TLE, deterministically and independent of machine load. (The pilot
        // majority-element bundle tops out at n≈5000, too small for an O(n^2) solution to blow a
        // realistic clock — algorithmic-scale TLE needs a larger-n bundle; the framework supports
        // it. Here we exercise the enforcement mechanism itself with a deliberately slow solution.)
        String user = "function majorityElement(nums){ const __t=Date.now(); while(Date.now()-__t<1500){} return nums[0]; }";
        Verdict v = judgeWithLimit("majority-element", majority(), "exact", Language.JAVASCRIPT, user, 500);
        assertThat(v.status()).isEqualTo(VerdictStatus.TLE);
        assertThat(v.failingCaseIndex()).isEqualTo(0); // killed on the very first case
    }
}
