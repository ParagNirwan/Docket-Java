-- V6: Add AI conversation history tables (Issue #12)

CREATE TABLE conversations (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    user_id     UUID         NOT NULL REFERENCES users(id),
    title       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_conversations_tenant_user ON conversations (tenant_id, user_id);
CREATE INDEX idx_conversations_updated ON conversations (updated_at DESC);

CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(10)  NOT NULL,
    content         TEXT         NOT NULL,
    sources_json    TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_conv_messages_conversation ON conversation_messages (conversation_id, created_at);
