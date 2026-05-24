CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL,
    org_id          UUID,
    user_id         UUID,
    title           VARCHAR(512) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_workspace_updated
    ON conversations (workspace_id, updated_at DESC);

CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role                VARCHAR(32) NOT NULL,
    content             TEXT NOT NULL,
    status              VARCHAR(64),
    pending_approval_id UUID,
    tool_name           VARCHAR(256),
    connector_key       VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at ASC);
