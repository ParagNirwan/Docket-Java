-- V9: Outbox for transactional email (Issue #55)
--
-- Emails are written here in the same transaction as the action that caused them,
-- so a crash or a provider outage delays an email instead of losing it.
-- Bodies are cleared once an email is finished, because they can hold reset links.

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    to_address VARCHAR(255) NOT NULL,
    template VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    html_body TEXT,
    text_body TEXT,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
