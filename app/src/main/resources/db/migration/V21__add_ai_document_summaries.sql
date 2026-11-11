-- V21: AI-written document summaries (Issue #81)

-- The embedding lets a broad workspace question ("which documents talk about X?") match a
-- whole document, not only one of its chunks.
ALTER TABLE documents
    ADD COLUMN ai_summary              TEXT,
    ADD COLUMN ai_key_points           JSONB,
    ADD COLUMN ai_summary_embedding    vector(768),
    ADD COLUMN ai_summary_generated_at TIMESTAMP WITH TIME ZONE;

-- An organization can turn summaries off, for cost or privacy
ALTER TABLE tenants
    ADD COLUMN ai_summaries_enabled BOOLEAN NOT NULL DEFAULT TRUE;
