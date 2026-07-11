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

/** Design-problem (operations) dispatch, executed live for the two runnable languages. */
class OperationsDriverExecIT {

    private final JudgeDriverGenerator gen = new JudgeDriverGenerator();
    private final ProcessManager pm = new ProcessManager();

    private String runCase(LanguageExecutor exec, String program, String stdinJson) throws Exception {
        FileManager fm = new FileManager(System.getProperty("java.io.tmpdir") + "/collide-judge-ops");
        try (Workspace ws = fm.create(java.util.UUID.randomUUID())) {
            fm.writeFile(ws, exec.sourceFilename(), program);
            if (exec.requiresCompilation()) {
                ProcessResult c = exec.compile(ws, pm, 20000, 1_000_000);
                assertThat(c.exitCode()).as("compile stderr: %s", c.stderr()).isZero();
            }
            Path stdin = fm.writeFile(ws, "stdin.txt", stdinJson);
            return pm.run(exec.runCommand(ws), ws.root(), stdin, 10000, 1_000_000).stdout().trim();
        }
    }

    private ProblemHarness minStack() {
        return new ProblemHarness("MinStack", List.of(new ProblemHarness.Param("ops", "operations")),
                "operations", List.of(), "exact", 2000, null);
    }

    private static final String STDIN =
            "[[[\"MinStack\",[]],[\"push\",[-2]],[\"push\",[0]],[\"push\",[-3]],[\"getMin\",[]],[\"pop\",[]],[\"top\",[]],[\"getMin\",[]]]]";
    private static final String EXPECTED = "[null,null,null,null,-3,null,0,-2]";

    @Test
    void jsOperationsDispatch() throws Exception {
        String user = "class MinStack{ constructor(){ this.s=[]; this.m=[]; } push(x){ this.s.push(x); this.m.push(this.m.length?Math.min(x,this.m[this.m.length-1]):x); } pop(){ this.s.pop(); this.m.pop(); } top(){ return this.s[this.s.length-1]; } getMin(){ return this.m[this.m.length-1]; } }";
        String program = gen.generate(Language.JAVASCRIPT, minStack(), user);
        assertThat(runCase(new NodeExecutor("node"), program, STDIN)).isEqualTo(EXPECTED);
    }

    @Test
    void javaOperationsDispatchWithVoidVsValueDetection() throws Exception {
        String user = "class MinStack { java.util.Deque<Integer> s=new java.util.ArrayDeque<>(); java.util.Deque<Integer> m=new java.util.ArrayDeque<>(); public MinStack(){} public void push(int x){ s.push(x); m.push(m.isEmpty()?x:Math.min(x,m.peek())); } public void pop(){ s.pop(); m.pop(); } public int top(){ return s.peek(); } public int getMin(){ return m.peek(); } }";
        String program = gen.generate(Language.JAVA, minStack(), user);
        assertThat(runCase(new JavaExecutor("javac", "java"), program, STDIN)).isEqualTo(EXPECTED);
    }
}
