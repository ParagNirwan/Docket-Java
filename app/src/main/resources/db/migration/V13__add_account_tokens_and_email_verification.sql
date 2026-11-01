-- V13: Password reset and email verification (Issue #75)
--
-- Existing users stay unverified: nobody has proven they own their address yet.

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE account_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_account_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_account_tokens_user_purpose ON account_tokens (user_id, purpose);
CREATE INDEX idx_account_tokens_expires ON account_tokens (expires_at);
