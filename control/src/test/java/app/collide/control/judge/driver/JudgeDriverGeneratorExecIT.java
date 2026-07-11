package app.collide.control.judge.driver;

import static org.assertj.core.api.Assertions.assertThat;

import app.collide.control.execution.executor.JavaExecutor;
import app.collide.control.execution.executor.LanguageExecutor;
import app.collide.control.execution.executor.NodeExecutor;
import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.workspace.FileManager;
import app.collide.control.execution.workspace.Workspace;
import app.collide.control.problem.ProblemHarness;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Compiles + runs REAL Node and Java driver programs (the two toolchains present in this env). */
class JudgeDriverGeneratorExecIT {

    private final JudgeDriverGenerator gen = new JudgeDriverGenerator();
    private final ProcessManager pm = new ProcessManager();

    private String runCase(LanguageExecutor exec, String program, String stdinJson) throws Exception {
        FileManager fm = new FileManager(System.getProperty("java.io.tmpdir") + "/collide-judge-test");
        try (Workspace ws = fm.create(java.util.UUID.randomUUID())) {
            fm.writeFile(ws, exec.sourceFilename(), program);
            if (exec.requiresCompilation()) {
                ProcessResult c = exec.compile(ws, pm, 20000, 1_000_000);
                assertThat(c.exitCode()).as("compile stderr: %s", c.stderr()).isZero();
            }
            Path stdin = fm.writeFile(ws, "stdin.txt", stdinJson);
            ProcessResult r = pm.run(exec.runCommand(ws), ws.root(), stdin, 10000, 1_000_000);
            assertThat(r.stderr()).as("run stderr: %s", r.stderr()).isEmpty();
            return r.stdout().trim();
        }
    }

    private ProblemHarness twoSum() {
        return new ProblemHarness("twoSum",
                List.of(new ProblemHarness.Param("nums", "int[]"), new ProblemHarness.Param("target", "int")),
                "int[]", List.of(), "unordered", 2000, null);
    }

    private ProblemHarness mergeLists() {
        return new ProblemHarness("mergeTwoLists",
                List.of(new ProblemHarness.Param("l1", "list-node<int>"), new ProblemHarness.Param("l2", "list-node<int>")),
                "list-node<int>", List.of(), "exact", 2000, null);
    }

    @Test
    void jsDriverSolvesTwoSumCase() throws Exception {
        String user = "function twoSum(nums, target){ const m=new Map(); for(let i=0;i<nums.length;i++){ if(m.has(target-nums[i])) return [m.get(target-nums[i]), i]; m.set(nums[i], i);} return []; }";
        String program = gen.generate(Language.JAVASCRIPT, twoSum(), user);
        assertThat(runCase(new NodeExecutor("node"), program, "[[2,7,11,15],9]")).isEqualTo("[0,1]");
    }

    @Test
    void jsDriverSolvesMergeListsCaseWithListNodeSerde() throws Exception {
        String user = "function mergeTwoLists(a,b){ const d={val:0,next:null}; let c=d; while(a&&b){ if(a.val<=b.val){c.next=a;a=a.next;}else{c.next=b;b=b.next;} c=c.next;} c.next=a||b; return d.next; }";
        String program = gen.generate(Language.JAVASCRIPT, mergeLists(), user);
        assertThat(runCase(new NodeExecutor("node"), program, "[[1,2,4],[1,3,4]]")).isEqualTo("[1,1,2,3,4,4]");
    }

    @Test
    void javaDriverSolvesTwoSumCase() throws Exception {
        String user = "class Solution { public int[] twoSum(int[] nums, int target){ java.util.Map<Integer,Integer> m=new java.util.HashMap<>(); for(int i=0;i<nums.length;i++){ if(m.containsKey(target-nums[i])) return new int[]{m.get(target-nums[i]), i}; m.put(nums[i], i);} return new int[]{}; } }";
        String program = gen.generate(Language.JAVA, twoSum(), user);
        assertThat(runCase(new JavaExecutor("javac", "java"), program, "[[2,7,11,15],9]")).isEqualTo("[0,1]");
    }

    @Test
    void javaDriverSolvesMergeListsCaseWithListNodeSerde() throws Exception {
        String user = "class Solution { public ListNode mergeTwoLists(ListNode a, ListNode b){ ListNode d=new ListNode(0), c=d; while(a!=null&&b!=null){ if(a.val<=b.val){c.next=a;a=a.next;}else{c.next=b;b=b.next;} c=c.next;} c.next=(a!=null)?a:b; return d.next; } }";
        String program = gen.generate(Language.JAVA, mergeLists(), user);
        assertThat(runCase(new JavaExecutor("javac", "java"), program, "[[1,2,4],[1,3,4]]")).isEqualTo("[1,1,2,3,4,4]");
    }
}
