package app.collide.control.problem.bundle;

/**
 * Read access to bundle artifacts, abstracted so the storage backend is swappable — mirroring the
 * collab server's {@code DocStore}/{@code PubSub} interface-with-fallback pattern. The zero-infra
 * default is {@link LocalBundleStore} (filesystem / mounted volume). An S3/MinIO implementation
 * swaps in later (master spec §11) without touching the seeder or the SP4 judge.
 *
 * <p>The write side lives in the Node {@code testgen} pipeline; this is the read side the control
 * plane and judge consume. {@code storageKey} is the opaque key recorded in the registry
 * (e.g. {@code "two-sum.v1.json.gz"}).
 */
public interface BundleStore {

    /** Load the raw (gzipped) bytes for a bundle. Throws if the key is absent. */
    byte[] load(String storageKey);

    /** Whether a bundle artifact exists for the given key. */
    boolean exists(String storageKey);
}
