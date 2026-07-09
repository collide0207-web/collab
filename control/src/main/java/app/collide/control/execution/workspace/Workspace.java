package app.collide.control.execution.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A private temp directory for exactly one execution. {@link #close()} always recursively
 * deletes it, including on timeout/exception — callers must use try-with-resources so no
 * execution's files can ever leak into or be visible to another execution.
 */
public final class Workspace implements AutoCloseable {

    private final Path root;

    Workspace(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path resolve(String filename) {
        return root.resolve(filename);
    }

    @Override
    public void close() {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort cleanup — a stray locked file must not fail the request.
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
