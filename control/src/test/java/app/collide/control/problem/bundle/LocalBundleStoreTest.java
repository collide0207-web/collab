package app.collide.control.problem.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DB-free unit tests for the filesystem bundle store: round-trip, existence, traversal guard. */
class LocalBundleStoreTest {

    @Test
    void writesAndLoadsGzippedBytesRoundTrip(@TempDir Path dir) throws Exception {
        String payload = "[{\"input\":[1],\"expected\":1}]";
        byte[] gz = gzip(payload);
        Files.write(dir.resolve("demo.v1.json.gz"), gz);

        LocalBundleStore store = new LocalBundleStore(dir);

        assertThat(store.exists("demo.v1.json.gz")).isTrue();
        assertThat(store.exists("missing.v1.json.gz")).isFalse();
        assertThat(gunzip(store.load("demo.v1.json.gz"))).isEqualTo(payload);
    }

    @Test
    void rejectsKeysThatEscapeTheRoot(@TempDir Path dir) {
        LocalBundleStore store = new LocalBundleStore(dir);
        assertThatThrownBy(() -> store.load("../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] gzip(String s) throws Exception {
        var bos = new java.io.ByteArrayOutputStream();
        try (var gzos = new GZIPOutputStream(bos)) {
            gzos.write(s.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    private static String gunzip(byte[] gz) throws Exception {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
