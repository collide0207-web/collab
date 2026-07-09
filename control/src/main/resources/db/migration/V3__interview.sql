-- Interview questions live in the control plane so they're durable, access-controlled
-- and reusable — unlike the previous approach of stashing them in the collab Yjs doc.
--
-- One row per room holds the whole authored question set as JSONB (matches the SPA's
-- shape: [{ title, description, fnName, tests[], images[] }]). Reference images are
-- stored as binary and served by id, so the question payload stays small.

CREATE TABLE interview_questions (
    room_id    UUID PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE,
    data       JSONB NOT NULL,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE interview_images (
    id           UUID PRIMARY KEY,
    room_id      UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    content_type TEXT NOT NULL,
    bytes        BYTEA NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_interview_images_room ON interview_images(room_id);
