ALTER TABLE idea ADD COLUMN IF NOT EXISTS star_count INTEGER NOT NULL DEFAULT 0 CHECK (star_count >= 0);

CREATE TABLE user_stars (
    id          BIGSERIAL PRIMARY KEY,
    idea_id     BIGINT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
    fingerprint VARCHAR(64) NOT NULL,
    starred_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (idea_id, fingerprint)
);

CREATE INDEX idx_user_stars_idea_id ON user_stars (idea_id);
