package app.collide.control.execution.executor;

import app.collide.control.execution.model.Language;
import app.collide.control.execution.workspace.Workspace;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * No compile step — `node main.js` runs the submitted source directly. Async code and
 * Promises work as-is since Node's event loop keeps the process alive until they settle.
 * Submitted source is treated as CommonJS (Node's default for a bare .js file with no
 * package.json); top-level ESM `import`/`export` syntax isn't auto-detected in this pass —
 * dynamic `import()` works today, static ESM support is a follow-up if needed.
 */
@Component
public class NodeExecutor implements LanguageExecutor {

    private final String nodeCommand;

    public NodeExecutor(@Value("${collide.execution.node.command:node}") String nodeCommand) {
        this.nodeCommand = nodeCommand;
    }

    @Override
    public Language language() {
        return Language.JAVASCRIPT;
    }

    @Override
    public String sourceFilename() {
        return "main.js";
    }

    @Override
    public List<String> runCommand(Workspace workspace) {
        return List.of(nodeCommand, workspace.resolve(sourceFilename()).toString());
    }
}
