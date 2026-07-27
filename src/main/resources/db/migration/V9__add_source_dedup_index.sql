-- Speeds up pipeline deduplication checks (existsBySourceUrlAndSourceTrack,
-- existsByRawSignalAndSourceTrack) which are called for every raw signal.
CREATE INDEX IF NOT EXISTS idx_idea_source_url_track
    ON idea (source_url, source_track)
    WHERE source_url IS NOT NULL;
