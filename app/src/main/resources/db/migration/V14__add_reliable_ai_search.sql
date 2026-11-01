-- V14: Reliable AI search (Issues #33, #35, #37, #40)

-- Which documents still need re-embedding, and a lease so that several pods can share the work
ALTER TABLE documents
    ADD COLUMN embedding_version  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reindex_attempts   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reindex_claimed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_documents_embedding_version ON documents (embedding_version) WHERE status = 'ACTIVE';

-- Where each chunk came from. Offsets are positions in the document's extracted text.
ALTER TABLE document_chunks
    ADD COLUMN page_start INTEGER,
    ADD COLUMN page_end   INTEGER,
    ADD COLUMN heading    VARCHAR(500),
    ADD COLUMN char_start INTEGER,
    ADD COLUMN char_end   INTEGER;

-- 'english' drops stop words, so that a question's "what is the" doesn't match every chunk.
-- Identifiers such as INV-2024-0042 are not stemmed and still match exactly.
ALTER TABLE document_chunks
    ADD COLUMN tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', chunk_text)) STORED;

CREATE INDEX idx_chunks_tsv ON document_chunks USING gin (tsv);

-- The standalone question that was used for retrieval, kept for debugging
ALTER TABLE conversation_messages
    ADD COLUMN rewritten_query TEXT;
