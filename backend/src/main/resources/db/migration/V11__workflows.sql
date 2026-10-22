CREATE TABLE workflow_rules (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    condition_type VARCHAR(40) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_workflow_rules_org_event UNIQUE (organization_id, event_type),
    CONSTRAINT chk_workflow_rules_event CHECK (event_type IN (
        'TASK_ASSIGNED',
        'TASK_OVERDUE',
        'DOCUMENT_UPLOADED',
        'DOCUMENT_PROCESSED',
        'SCHEDULED_ACTIVITY'
    )),
    CONSTRAINT chk_workflow_rules_condition CHECK (condition_type IN (
        'ALWAYS',
        'HAS_ASSIGNEE',
        'TEXT_EXTRACTED'
    )),
    CONSTRAINT chk_workflow_rules_action CHECK (action_type IN (
        'NOTIFY_ASSIGNEE',
        'NOTIFY_RELEVANT_USERS',
        'PROCESS_DOCUMENT',
        'SUMMARIZE_DOCUMENT',
        'SUMMARIZE_ACTIVITY'
    ))
);

CREATE TABLE workflow_executions (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES workflow_rules(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    error_message VARCHAR(300),
    result_message VARCHAR(300),
    task_id UUID,
    assignee_id UUID,
    document_id UUID,
    actor_id UUID,
    anchor_date DATE,
    depth INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_workflow_executions_rule_key UNIQUE (rule_id, idempotency_key),
    CONSTRAINT chk_workflow_executions_status CHECK (status IN (
        'RUNNING',
        'SUCCEEDED',
        'FAILED',
        'SKIPPED'
    ))
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    execution_id UUID REFERENCES workflow_executions(id) ON DELETE SET NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notifications_execution_user UNIQUE (execution_id, user_id)
);

CREATE INDEX idx_workflow_executions_org_created ON workflow_executions(organization_id, created_at);
CREATE INDEX idx_workflow_executions_retry ON workflow_executions(status, next_attempt_at);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);

ALTER TABLE activity_events DROP CONSTRAINT chk_activity_events_type;
ALTER TABLE activity_events ADD CONSTRAINT chk_activity_events_type CHECK (event_type IN (
    'TASK_CREATED',
    'TASK_UPDATED',
    'TASK_STATUS_CHANGED',
    'TASK_COMPLETED',
    'TASK_DELETED',
    'WORKFLOW_SUCCEEDED',
    'WORKFLOW_FAILED'
));
