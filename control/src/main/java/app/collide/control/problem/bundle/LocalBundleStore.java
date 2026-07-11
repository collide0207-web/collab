package app.collide.control.problem.bundle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem/mounted-volume {@link BundleStore} — the zero-infra default. Bundle artifacts live
 * under {@code root} keyed by their storage key; for the SP3 pilot {@code root} is the committed
 * {@code seed/test-bundles} resources directory, so the store works with no external infra.
 */
public class LocalBundleStore implements BundleStore {

    private final Path root;

    public LocalBundleStore(Path root) {
        this.root = root;
    }

    private Path resolve(String storageKey) {
        // Guard against path traversal escaping the root (keys come from the manifest, but be strict).
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root.normalize())) {
            throw new IllegalArgumentException("storageKey escapes bundle root: " + storageKey);
        }
        return p;
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load bundle " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }
}
