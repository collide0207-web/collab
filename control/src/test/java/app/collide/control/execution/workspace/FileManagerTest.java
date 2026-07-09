package app.collide.control.execution.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** No Spring context — {@link FileManager} is constructed directly with a temp root. */
class FileManagerTest {

    @Test
    void createsAnIsolatedWorkspacePerExecution(@TempDir Path root) throws IOException {
        FileManager files = new FileManager(root.toString());

        Workspace a = files.create(UUID.randomUUID());
        Workspace b = files.create(UUID.randomUUID());

        assertTrue(Files.exists(a.root()));
        assertTrue(Files.exists(b.root()));
        assertFalse(a.root().equals(b.root()), "each execution must get its own directory");
    }

    @Test
    void writesSourceFileContent(@TempDir Path root) throws IOException {
        FileManager files = new FileManager(root.toString());
        Workspace ws = files.create(UUID.randomUUID());

        Path source = files.writeFile(ws, "main.py", "print('hi')");

        assertEquals("print('hi')", Files.readString(source));
    }

    @Test
    void skipsStdinFileWhenInputIsEmpty(@TempDir Path root) throws IOException {
        FileManager files = new FileManager(root.toString());
        Workspace ws = files.create(UUID.randomUUID());

        assertNull(files.writeStdinIfPresent(ws, null));
        assertNull(files.writeStdinIfPresent(ws, ""));

        Path stdin = files.writeStdinIfPresent(ws, "5\n1 2 3 4 5");
        assertNotNull(stdin);
        assertEquals("5\n1 2 3 4 5", Files.readString(stdin));
    }

    @Test
    void closeDeletesTheWorkspaceRecursively(@TempDir Path root) throws IOException {
        FileManager files = new FileManager(root.toString());
        Workspace ws = files.create(UUID.randomUUID());
        files.writeFile(ws, "main.py", "print(1)");
        files.writeStdinIfPresent(ws, "input");

        ws.close();

        assertFalse(Files.exists(ws.root()), "workspace directory must be gone after close()");
    }
}
