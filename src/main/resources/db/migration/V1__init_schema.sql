CREATE TABLE idea
(
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255)             NOT NULL,
    description  TEXT                     NOT NULL,
    source_track VARCHAR(50)              NOT NULL,
    source_url   VARCHAR(1024),
    raw_signal   TEXT,
    score        SMALLINT,
    score_reason TEXT,
    status       VARCHAR(50)              NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idea_status ON idea (status);
CREATE INDEX idx_idea_source_track ON idea (source_track);
CREATE INDEX idx_idea_created_at ON idea (created_at DESC);
