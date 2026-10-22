CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(8000) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_conversation_messages_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_conversations_org_user_updated ON conversations(organization_id, user_id, updated_at);
CREATE INDEX idx_conversation_messages_conversation_created ON conversation_messages(conversation_id, created_at);
