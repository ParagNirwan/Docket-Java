-- V11: Refresh tokens and session revocation (Issue #78)
--
-- A family is one login: every rotation adds a row to it, so "session" and
-- "family" mean the same thing. Only hashes are stored, so a database leak
-- doesn't hand out working sessions.
--   - rotated_at: this token was exchanged for a newer one
--   - revoked_at: the whole session was ended

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    user_agent VARCHAR(512),
    ip VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- Access tokens carry this number. Raising it invalidates every token already issued.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
