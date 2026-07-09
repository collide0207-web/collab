package app.collide.control.execution.executor;

import app.collide.control.execution.model.Language;
import app.collide.control.execution.workspace.Workspace;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** No compile step — `python3 main.py` runs the submitted source directly. */
@Component
public class PythonExecutor implements LanguageExecutor {

    private final String pythonCommand;

    public PythonExecutor(@Value("${collide.execution.python.command:python3}") String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    @Override
    public String sourceFilename() {
        return "main.py";
    }

    @Override
    public List<String> runCommand(Workspace workspace) {
        return List.of(pythonCommand, workspace.resolve(sourceFilename()).toString());
    }
}
