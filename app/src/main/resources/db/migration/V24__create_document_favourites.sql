-- V24: Create document_favourites table matching DocumentFavourite entity (#72)
CREATE TABLE IF NOT EXISTS document_favourites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_document_favourites_user_tenant_doc UNIQUE (user_id, tenant_id, document_id)
);

CREATE INDEX IF NOT EXISTS idx_document_favourites_user_tenant ON document_favourites (user_id, tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_favourites_document ON document_favourites (document_id);
