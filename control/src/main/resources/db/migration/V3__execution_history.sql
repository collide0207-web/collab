-- V3: durable, paginated record of past code executions. The in-memory ExecutionRegistry
-- (see ExecutionRegistry.java) is the fast live-status path; this table is what GET /history
-- reads from and survives restarts/eviction of that in-memory map.

CREATE TABLE execution_history (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language          TEXT NOT NULL,
    source_code       TEXT,
    stdin             TEXT,
    stdout            TEXT,
    stderr            TEXT,
    exit_code         INT,
    status            TEXT NOT NULL,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    memory_kb         BIGINT,          -- not measured yet (process-based, no cgroup accounting)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_execution_history_user       ON execution_history(user_id);
CREATE INDEX idx_execution_history_user_time  ON execution_history(user_id, created_at DESC);
