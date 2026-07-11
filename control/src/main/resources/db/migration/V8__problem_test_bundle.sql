-- SP3 test-data pipeline registry. One row per generated hidden-test bundle (a gzipped
-- {input,expected}[] artifact produced by the testgen pipeline and stored via BundleStore).
-- The SP4 judge loads a problem's active bundle by (problem_slug, version); the checksum lets
-- a judge cache self-invalidate when a bundle is regenerated. Bundles themselves live in object
-- storage / a mounted volume (NOT this table), keyed by storage_key.
CREATE TABLE problem_test_bundle (
    id             BIGSERIAL PRIMARY KEY,
    problem_slug   VARCHAR(160) NOT NULL,
    version        INT NOT NULL,
    checksum       VARCHAR(64) NOT NULL,
    case_count     INT NOT NULL,
    storage_key    VARCHAR(300) NOT NULL,
    time_limit_ms  INT,
    checker_type   VARCHAR(40) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_problem_test_bundle_slug_version UNIQUE (problem_slug, version)
);

CREATE INDEX idx_problem_test_bundle_slug ON problem_test_bundle (problem_slug);
