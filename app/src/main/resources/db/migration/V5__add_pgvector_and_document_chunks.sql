-- V5: Add pgvector extension, indexing status, and document chunks table (Issue #7)

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE documents
    ADD COLUMN indexing_status VARCHAR(20) DEFAULT 'PENDING';

CREATE TABLE document_chunks (
    id             UUID PRIMARY KEY,
    tenant_id      UUID          NOT NULL REFERENCES tenants(id),
    document_id    UUID          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index    INTEGER       NOT NULL,
    chunk_text     TEXT          NOT NULL,
    embedding      vector(768),
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_chunks_tenant ON document_chunks (tenant_id);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
