CREATE TABLE gemini_usage (
    id               BIGSERIAL PRIMARY KEY,
    used_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    operation        VARCHAR(20) NOT NULL,
    prompt_tokens    INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens     INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_gemini_usage_used_at ON gemini_usage (used_at);
