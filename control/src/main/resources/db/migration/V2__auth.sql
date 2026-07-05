-- V2: production auth. Extends the V1 `users` table with identity/provider/lifecycle
-- columns and adds the refresh-token, login-history and one-time-token tables.
-- Written to apply incrementally on top of a V1 database that may already hold rows.

-- ---------------------------------------------------------------------------
-- users: extend with provider, identity, verification and lifecycle columns
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS username        TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider   TEXT NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id       TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_picture TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS role            TEXT NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active       BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_deleted      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until    TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at   TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at      TIMESTAMPTZ NOT NULL DEFAULT now();
-- Optimistic-locking version column (Hibernate @Version) to prevent lost updates.
ALTER TABLE users ADD COLUMN IF NOT EXISTS version         BIGINT NOT NULL DEFAULT 0;

-- Backfill username for any pre-existing rows so we can enforce NOT NULL + UNIQUE.
-- Deterministic and collision-free: email local-part + a slice of the UUID.
UPDATE users
   SET username = regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9_.-]', '_', 'g')
                  || '_' || substr(id::text, 1, 8)
 WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;

-- Google-authenticated accounts have no local password.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Uniqueness for username and google_id (partial index so multiple NULL google_ids are fine).
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username  ON users (lower(username));
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_id ON users (google_id) WHERE google_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- refresh_tokens: one row per issued refresh token. Only the SHA-256 hash is
-- stored (never the raw token). Rotation links old -> new via replaced_by.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,             -- SHA-256 of the opaque token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by UUID REFERENCES refresh_tokens(id),  -- set when rotated
    device      TEXT,
    ip_address  TEXT,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------------------------------------------------------------------
-- login_history: append-only audit of every login attempt (success + failure).
-- user_id is nullable — a failed login for an unknown email has no user.
-- ---------------------------------------------------------------------------
CREATE TABLE login_history (
    id             UUID PRIMARY KEY,
    user_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    email          TEXT,
    ip             TEXT,
    user_agent     TEXT,
    login_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    success        BOOLEAN NOT NULL,
    failure_reason TEXT
);
CREATE INDEX idx_login_history_user ON login_history(user_id);
CREATE INDEX idx_login_history_time ON login_history(login_time);

-- ---------------------------------------------------------------------------
-- password_reset_tokens / email_verification_tokens: one-time tokens, hashed.
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);

CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens(user_id);
