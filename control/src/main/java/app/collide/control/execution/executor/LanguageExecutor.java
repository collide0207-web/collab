package app.collide.control.execution.executor;

import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.workspace.Workspace;
import java.io.IOException;
import java.util.List;

/**
 * One language's compile/run strategy. Everything language-specific (source filename,
 * compiler invocation, run invocation) lives behind this interface so
 * {@link app.collide.control.execution.ExecutionService} never branches on language —
 * adding a language is adding a new implementation, not editing the orchestrator.
 */
public interface LanguageExecutor {

    Language language();

    /** The filename the submitted source is written to inside the workspace (e.g. "main.py"). */
    String sourceFilename();

    default boolean requiresCompilation() {
        return false;
    }

    /** Only called when {@link #requiresCompilation()} is true. */
    default ProcessResult compile(Workspace workspace, ProcessManager processManager, long timeoutMs, long maxOutputBytes)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException(language() + " does not require compilation");
    }

    /** The command to run the (possibly just-compiled) program, resolved against the workspace. */
    List<String> runCommand(Workspace workspace);
}
