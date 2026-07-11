-- V9: authoritative Submit records (SP4). One row per Submit; the verdict is produced by the
-- server-side judge running the hidden test bundle. Mirrors execution_history's durability role
-- for the Submit tier. Hidden inputs are never stored here — only the aggregate verdict.

CREATE TABLE submissions (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_slug       VARCHAR(160) NOT NULL,
    language           TEXT NOT NULL,
    source_hash        VARCHAR(64) NOT NULL,
    status             TEXT NOT NULL,            -- PENDING | AC | WA | TLE | RE | CE
    verdict            TEXT,                     -- same as status once terminal; null while PENDING
    passed             INT NOT NULL DEFAULT 0,
    total              INT NOT NULL DEFAULT 0,
    failing_case_index INT NOT NULL DEFAULT -1,
    runtime_ms         BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_submissions_user            ON submissions(user_id);
CREATE INDEX idx_submissions_user_slug_time  ON submissions(user_id, problem_slug, created_at DESC);
