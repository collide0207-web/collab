package app.collide.control.problem.bundle;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default {@link BundleStore}. Root dir is {@code collide.testbundles.dir}, defaulting to
 * the committed pilot bundles under the resources tree so the app runs with zero infra. In a
 * deployment this points at a mounted volume; an S3-backed {@code BundleStore} bean can replace
 * this one without touching consumers.
 */
@Configuration
public class BundleStoreConfig {

    @Bean
    public BundleStore bundleStore(@Value("${collide.testbundles.dir:src/main/resources/seed/test-bundles}") String dir) {
        return new LocalBundleStore(Path.of(dir));
    }
}
