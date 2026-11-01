-- V12: Shared counters for auth rate limits and lockout (Issue #79)
--
-- Kept in Postgres rather than in memory so that the limits hold across backend
-- pods. bucket_key is a hash, so emails and IP addresses aren't stored here.

CREATE TABLE auth_rate_limits (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL
);

CREATE INDEX idx_auth_rate_limits_window ON auth_rate_limits (window_start);
