-- V15: Email invites, the members page and keyword search (Issues #56, #57, #68)

-- Only the hash of an invite's token is stored. The token itself is only ever in the email.
CREATE TABLE tenant_invitations (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    invited_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    status      VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE
);

-- One live invite per address per organization, even if two admins send one at the same moment
CREATE UNIQUE INDEX uk_tenant_invitations_pending
    ON tenant_invitations (tenant_id, LOWER(email))
    WHERE status = 'PENDING';

CREATE INDEX idx_tenant_invitations_expiry ON tenant_invitations (expires_at) WHERE status = 'PENDING';

-- Unknown for people who joined before this column existed
ALTER TABLE tenant_membership
    ADD COLUMN joined_at TIMESTAMP WITH TIME ZONE;

-- Invites are the first way to add someone to an existing organization, so this is the
-- first time two requests could race to add the same person. The authorization check
-- treats two active memberships as a broken configuration.
CREATE UNIQUE INDEX uk_tenant_membership_active_user
    ON tenant_membership (tenant_id, user_id)
    WHERE status = 'ACTIVE';

-- Trigram index for substring and misspelled filename search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_documents_file_name_trgm ON documents USING gin (original_file_name gin_trgm_ops);
