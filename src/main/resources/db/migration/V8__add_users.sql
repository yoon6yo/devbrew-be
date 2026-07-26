CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider ON users(provider, provider_id);
