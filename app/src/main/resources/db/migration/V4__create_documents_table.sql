-- V4: Create the documents table for file upload system (Issue #5)

CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    folder_id         UUID          NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    uploaded_by       UUID          REFERENCES users(id) ON DELETE SET NULL,
    original_file_name VARCHAR(512) NOT NULL,
    stored_file_name  VARCHAR(512)  NOT NULL,
    content_type      VARCHAR(255)  NOT NULL,
    file_size         BIGINT        NOT NULL,
    checksum_sha256   VARCHAR(64),
    status            VARCHAR(20)   NOT NULL DEFAULT 'UPLOADING',
    version           INTEGER       NOT NULL DEFAULT 1,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_documents_tenant_folder ON documents (tenant_id, folder_id);
CREATE INDEX idx_documents_tenant_status ON documents (tenant_id, status);
CREATE UNIQUE INDEX uk_documents_stored_filename ON documents (stored_file_name);
