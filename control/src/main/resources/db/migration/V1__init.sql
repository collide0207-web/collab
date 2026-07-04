-- Control-plane schema. Lives in the same Postgres as the sync server's
-- documents/document_updates tables, but is fully independent of them.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rooms (
    id         UUID PRIMARY KEY,
    name       TEXT NOT NULL,
    mode       TEXT NOT NULL DEFAULT 'group',   -- 'solo' | 'group'
    owner_id   UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE room_members (
    room_id    UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       TEXT NOT NULL,                    -- 'owner' | 'editor' | 'viewer'
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    PRIMARY KEY (room_id, user_id)
);
CREATE INDEX idx_room_members_user ON room_members(user_id);

CREATE TABLE share_links (
    id         UUID PRIMARY KEY,
    room_id    UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    role       TEXT NOT NULL,                    -- role granted on redemption
    token_hash TEXT NOT NULL UNIQUE,             -- SHA-256 of the opaque token
    expires_at TIMESTAMPTZ,
    revoked    BOOLEAN NOT NULL DEFAULT false,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_share_links_room ON share_links(room_id);
