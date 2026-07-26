CREATE INDEX IF NOT EXISTS idx_idea_status_score ON idea (status, score DESC NULLS LAST);
