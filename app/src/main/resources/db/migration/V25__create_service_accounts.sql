-- V25: Create service_accounts table for Service Account authentication (#124)
CREATE TABLE IF NOT EXISTS service_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    client_id       VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(64) NOT NULL,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    revoked_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_service_accounts_tenant ON service_accounts (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_service_accounts_client_id ON service_accounts (client_id);