package app.collide.control.execution.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates and populates the per-execution temp workspace. Every execution gets its own
 * directory under {@code collide.execution.workspace-root} — no execution's files are ever
 * visible to, or can affect, another execution's.
 */
@Component
public class FileManager {

    private final Path workspaceRoot;

    public FileManager(@Value("${collide.execution.workspace-root:${java.io.tmpdir}/collide-exec}") String workspaceRoot)
            throws IOException {
        this.workspaceRoot = Path.of(workspaceRoot);
        Files.createDirectories(this.workspaceRoot);
    }

    public Workspace create(UUID executionId) throws IOException {
        Path dir = Files.createTempDirectory(workspaceRoot, "exec-" + executionId + "-");
        return new Workspace(dir);
    }

    public Path writeFile(Workspace workspace, String filename, String content) throws IOException {
        Path path = workspace.resolve(filename);
        Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        return path;
    }

    /** Writes stdin content if non-empty; returns null when there is nothing to feed the process. */
    public Path writeStdinIfPresent(Workspace workspace, String stdin) throws IOException {
        if (stdin == null || stdin.isEmpty()) {
            return null;
        }
        return writeFile(workspace, "stdin.txt", stdin);
    }
}
