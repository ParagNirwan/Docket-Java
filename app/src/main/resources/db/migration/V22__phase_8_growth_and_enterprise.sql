-- V22: Phase 8, growth and enterprise
-- Covers: #59 Invite links, #60 Domain auto-join, #63 Bulk CSV invites, #77 Single sign-on,
--         #70 Duplicate detection, #72 Favourites and recent files, #87 Activity feed,
--         #54 GCS object holds, #82 Classification and tags, #83 Key-field extraction,
--         #84 Document comparison, #85 Shared conversations, #88 API keys and webhooks,
--         #89 Billing.

-- 1. Invite links (#59). Like an email invite, only the hash of the token is stored.
ALTER TABLE tenants ADD COLUMN invite_links_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE tenant_invite_links (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    role       VARCHAR(20) NOT NULL,
    max_uses   INTEGER,
    uses       INTEGER     NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_tenant_invite_links_tenant ON tenant_invite_links (tenant_id);

-- 2. Verified email domains (#60) and enforced single sign-on for them (#77)
CREATE TABLE tenant_domains (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    domain             VARCHAR(255) NOT NULL,
    verification_token VARCHAR(64)  NOT NULL,
    verified_at        TIMESTAMP WITH TIME ZONE,
    join_mode          VARCHAR(10)  NOT NULL DEFAULT 'REQUEST',
    sso_required       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX uk_tenant_domains_tenant_domain ON tenant_domains (tenant_id, LOWER(domain));
CREATE INDEX idx_tenant_domains_verified ON tenant_domains (LOWER(domain)) WHERE verified_at IS NOT NULL;

-- A request to join waits as a PENDING membership. One at a time per person.
CREATE UNIQUE INDEX uk_tenant_membership_pending_user
    ON tenant_membership (tenant_id, user_id)
    WHERE status = 'PENDING';

-- 3. Single sign-on identities (#77). The subject is the provider's own ID for the person,
-- which stays the same when their email address changes.
CREATE TABLE user_identities (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (provider, subject)
);
CREATE INDEX idx_user_identities_user ON user_identities (user_id);

-- 4. Duplicate detection (#70)
CREATE INDEX idx_documents_tenant_checksum ON documents (tenant_id, checksum_sha256) WHERE status = 'ACTIVE';

-- 5. Favourites (#72). Recent files are read from the audit log, which this index serves.
CREATE TABLE user_favourites (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_type VARCHAR(10) NOT NULL,
    target_id   UUID        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, tenant_id, target_type, target_id)
);

CREATE INDEX idx_audit_events_recent_documents
    ON audit_events (tenant_id, actor_user_id, created_at DESC)
    WHERE action IN ('DOCUMENT_VIEWED', 'DOCUMENT_DOWNLOADED');

-- 6. Storage holds (#54). Whether the stored file currently carries a hold in the bucket.
ALTER TABLE documents ADD COLUMN storage_hold BOOLEAN NOT NULL DEFAULT FALSE;

-- 7. AI classification and extracted fields (#82, #83). ai_fields is what the AI proposed.
-- key_date is set only once a person has confirmed the fields, because retention and
-- reminders act on it.
ALTER TABLE documents
    ADD COLUMN ai_document_type      VARCHAR(20),
    ADD COLUMN ai_suggested_tags     JSONB,
    ADD COLUMN ai_fields             JSONB,
    ADD COLUMN ai_fields_reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN key_date              DATE,
    ADD COLUMN key_date_notified_at  TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_documents_key_date ON documents (key_date)
    WHERE key_date IS NOT NULL AND key_date_notified_at IS NULL;

ALTER TABLE tenants
    ADD COLUMN ai_classification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN ai_auto_apply_tags        BOOLEAN NOT NULL DEFAULT FALSE;

-- 8. Shared conversations and saved answers (#85)
ALTER TABLE conversations ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';
CREATE INDEX idx_conversations_tenant_shared ON conversations (tenant_id, updated_at DESC) WHERE visibility = 'TENANT';

ALTER TABLE conversation_messages
    ADD COLUMN pinned_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pinned_by UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_conv_messages_pinned ON conversation_messages (pinned_at DESC) WHERE pinned_at IS NOT NULL;

-- 9. API keys and webhooks (#88)
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    prefix       VARCHAR(16)  NOT NULL,
    scopes       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at   TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_api_keys_tenant ON api_keys (tenant_id);

CREATE TABLE webhook_endpoints (
    id         UUID PRIMARY KEY,
    tenant_id  UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    url        VARCHAR(2048) NOT NULL,
    secret     VARCHAR(64)   NOT NULL,
    events     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by UUID          REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_webhook_endpoints_tenant ON webhook_endpoints (tenant_id);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY,
    endpoint_id     UUID        NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type      VARCHAR(40) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(10) NOT NULL,
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    response_status INTEGER,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_deliveries_endpoint ON webhook_deliveries (endpoint_id, created_at DESC);

-- 10. Billing (#89). Every organization starts on Free. Limits apply only where billing is on.
ALTER TABLE tenants
    ADD COLUMN plan                   VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN stripe_customer_id     VARCHAR(64),
    ADD COLUMN stripe_subscription_id VARCHAR(64),
    ADD COLUMN subscription_status    VARCHAR(30),
    ADD COLUMN plan_renews_at         TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX uk_tenants_stripe_customer ON tenants (stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;

-- AI questions asked per calendar month, for the plan's allowance
CREATE TABLE tenant_ai_usage (
    tenant_id UUID    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    period    DATE    NOT NULL,
    queries   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, period)
);
