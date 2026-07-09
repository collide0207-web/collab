-- Coding-practice problems (NeetCode 150 to start) + per-user progress.
--
-- Sheet-agnostic by design: a `sheet` column lets other sheets (Blind 75, etc.)
-- share the table later with no migration. Flexible fields (tags, examples,
-- starter code, supported languages) are JSONB so the shape can evolve without
-- schema churn. Statement text (description/constraints/examples) is nullable —
-- rows seed as browsable metadata and get original statements backfilled.

CREATE TABLE problems (
    id                   UUID PRIMARY KEY,
    sheet                TEXT NOT NULL DEFAULT 'neetcode150',
    slug                 TEXT NOT NULL UNIQUE,
    title                TEXT NOT NULL,
    difficulty           TEXT NOT NULL,              -- 'easy' | 'medium' | 'hard'
    category             TEXT NOT NULL,
    tags                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    description          TEXT,
    examples             JSONB,                      -- [{ input, output, explanation? }]
    constraints          TEXT,
    source_url           TEXT,
    starter_code         JSONB NOT NULL DEFAULT '{}'::jsonb,   -- { lang: code }
    supported_languages  JSONB NOT NULL DEFAULT '[]'::jsonb,   -- [lang]
    order_index          INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_problems_sheet ON problems(sheet);
CREATE INDEX idx_problems_category ON problems(category);
CREATE INDEX idx_problems_difficulty ON problems(difficulty);

CREATE TABLE user_progress (
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id     UUID NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    status         TEXT NOT NULL DEFAULT 'unsolved',  -- unsolved | attempted | solved
    language       TEXT,                              -- last selected language
    code           JSONB NOT NULL DEFAULT '{}'::jsonb,-- { lang: source } (per-language)
    favorite       BOOLEAN NOT NULL DEFAULT false,
    completed      BOOLEAN NOT NULL DEFAULT false,
    time_spent     INT NOT NULL DEFAULT 0,            -- seconds
    attempt_count  INT NOT NULL DEFAULT 0,
    run_count      INT NOT NULL DEFAULT 0,
    last_opened    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, problem_id)
);
CREATE INDEX idx_user_progress_user ON user_progress(user_id);
CREATE INDEX idx_user_progress_favorite ON user_progress(user_id, favorite);
