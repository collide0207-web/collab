package app.collide.control.execution.executor;

import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.workspace.Workspace;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * `javac Main.java`, then `java -cp . Main`. The submitted source must declare its public
 * class as {@code Main} (single-file submission, matching the execution request's single
 * `sourceCode` field) but may declare additional non-public top-level classes in the same
 * file — that covers the spec's "multiple classes" requirement without needing a multi-file
 * project model, which is out of scope for this pass.
 */
@Component
public class JavaExecutor implements LanguageExecutor {

    private final String compiler;
    private final String runtime;

    public JavaExecutor(
            @Value("${collide.execution.java.compiler:javac}") String compiler,
            @Value("${collide.execution.java.runtime:java}") String runtime) {
        this.compiler = compiler;
        this.runtime = runtime;
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public String sourceFilename() {
        return "Main.java";
    }

    @Override
    public boolean requiresCompilation() {
        return true;
    }

    @Override
    public ProcessResult compile(Workspace workspace, ProcessManager processManager, long timeoutMs, long maxOutputBytes)
            throws IOException, InterruptedException {
        List<String> command = List.of(compiler, sourceFilename());
        return processManager.run(command, workspace.root(), null, timeoutMs, maxOutputBytes);
    }

    @Override
    public List<String> runCommand(Workspace workspace) {
        return List.of(runtime, "-cp", ".", "Main");
    }
}
