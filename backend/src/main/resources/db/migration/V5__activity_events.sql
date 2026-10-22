CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id),
    task_id UUID,
    event_type VARCHAR(40) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    occurred_on DATE NOT NULL,
    CONSTRAINT chk_activity_events_type CHECK (event_type IN (
        'TASK_CREATED',
        'TASK_UPDATED',
        'TASK_STATUS_CHANGED',
        'TASK_COMPLETED',
        'TASK_DELETED'
    ))
);

CREATE INDEX idx_activity_events_org_occurred ON activity_events(organization_id, occurred_at);
CREATE INDEX idx_activity_events_org_type_day ON activity_events(organization_id, event_type, occurred_on);
CREATE INDEX idx_tasks_org_status_due ON tasks(organization_id, status, due_date);
