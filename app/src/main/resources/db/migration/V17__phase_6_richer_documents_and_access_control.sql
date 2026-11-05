-- Phase 6: Richer documents, access control and protection
-- Covers: #61 Guest users, #62 Custom tray permissions, #64 Versioning, #65 In-app preview,
--         #67 Tags and metadata, #69 Bulk actions, #71 External share links,
--         #74 Storage quotas, #76 Two-factor authentication, #80 Malware scanning.

-- 1. Malware scanning & metadata (#67, #80)
ALTER TABLE documents ADD COLUMN IF NOT EXISTS scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS scanned_at TIMESTAMPTZ;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS scan_detail VARCHAR(500);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_documents_scan_pending ON documents(scan_status) WHERE scan_status = 'PENDING';

-- Existing documents in the database are marked CLEAN
UPDATE documents SET scan_status = 'CLEAN', scanned_at = now() WHERE scan_status = 'PENDING' AND scanned_at IS NULL;

-- 2. Document Versioning (#64, #80)
CREATE TABLE IF NOT EXISTS document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    stored_file_name VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64),
    content_type VARCHAR(255) NOT NULL,
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    comment VARCHAR(500),
    scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scanned_at TIMESTAMPTZ,
    UNIQUE(document_id, version)
);
CREATE INDEX IF NOT EXISTS idx_document_versions_pending_scan ON document_versions(scan_status) WHERE scan_status = 'PENDING';

-- 3. Tags & Document Tags (#67)
CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    color VARCHAR(7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_tenant_lower_name ON tags (tenant_id, lower(name));

CREATE TABLE IF NOT EXISTS document_tags (
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(document_id, tag_id)
);
CREATE INDEX IF NOT EXISTS idx_document_tags_tag ON document_tags(tag_id);

-- 4. External Share Links (#71)
CREATE TABLE IF NOT EXISTS external_share_links (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ,
    password_hash VARCHAR(100),
    max_downloads INTEGER,
    download_count INTEGER NOT NULL DEFAULT 0,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_share_links_token_hash ON external_share_links(token_hash);
CREATE INDEX IF NOT EXISTS idx_share_links_document ON external_share_links(document_id);

-- 5. Storage Quotas & Tenant Usage (#74)
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS storage_quota_bytes BIGINT;
CREATE TABLE IF NOT EXISTS tenant_usage (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    storage_bytes_used BIGINT NOT NULL DEFAULT 0,
    document_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed usage for existing tenants
INSERT INTO tenant_usage (tenant_id, storage_bytes_used, document_count, updated_at)
SELECT t.id,
       COALESCE(SUM(d.file_size), 0),
       COALESCE(COUNT(d.id), 0),
       now()
FROM tenants t
LEFT JOIN documents d ON d.tenant_id = t.id AND d.status = 'ACTIVE'
GROUP BY t.id
ON CONFLICT (tenant_id) DO NOTHING;

-- 6. Custom Tray Permissions (#62)
CREATE TABLE IF NOT EXISTS tray_permissions (
    tray_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(tray_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_tray_permissions_user ON tray_permissions(user_id);

-- 7. Two-Factor Authentication (#76)
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_backup_codes JSONB NOT NULL DEFAULT '[]'::jsonb;
