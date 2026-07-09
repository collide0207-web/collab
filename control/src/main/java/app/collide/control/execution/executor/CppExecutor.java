package app.collide.control.execution.executor;

import app.collide.control.execution.model.Language;
import app.collide.control.execution.process.ProcessManager;
import app.collide.control.execution.process.ProcessResult;
import app.collide.control.execution.workspace.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** `g++ main.cpp -std=c++20 -O2 -o main`, then run the produced binary. */
@Component
public class CppExecutor implements LanguageExecutor {

    private final String compiler;

    public CppExecutor(@Value("${collide.execution.cpp.compiler:g++}") String compiler) {
        this.compiler = compiler;
    }

    @Override
    public Language language() {
        return Language.CPP;
    }

    @Override
    public String sourceFilename() {
        return "main.cpp";
    }

    @Override
    public boolean requiresCompilation() {
        return true;
    }

    @Override
    public ProcessResult compile(Workspace workspace, ProcessManager processManager, long timeoutMs, long maxOutputBytes)
            throws IOException, InterruptedException {
        List<String> command = List.of(compiler, sourceFilename(), "-std=c++20", "-O2", "-o", "main");
        return processManager.run(command, workspace.root(), null, timeoutMs, maxOutputBytes);
    }

    @Override
    public List<String> runCommand(Workspace workspace) {
        // MinGW g++ on Windows appends .exe to the -o name; GCC on Linux/macOS does not.
        Path windowsBinary = workspace.resolve("main.exe");
        Path binary = Files.exists(windowsBinary) ? windowsBinary : workspace.resolve("main");
        return List.of(binary.toString());
    }
}
