CREATE TABLE assistant_tool_calls (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES conversation_messages(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tool_name VARCHAR(40) NOT NULL,
    arguments_json VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json VARCHAR(8000),
    error_message VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_assistant_tool_calls_status CHECK (status IN (
        'PROPOSED',
        'EXECUTING',
        'COMPLETED',
        'REJECTED',
        'FAILED'
    ))
);

CREATE INDEX idx_assistant_tool_calls_message ON assistant_tool_calls(message_id);
