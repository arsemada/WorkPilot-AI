CREATE TABLE task_comments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_comments_task_created ON task_comments(task_id, created_at);

CREATE TABLE task_document_links (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    attached_by_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_document_links UNIQUE (task_id, document_id)
);

CREATE INDEX idx_task_document_links_task ON task_document_links(task_id);
CREATE INDEX idx_task_document_links_document ON task_document_links(document_id);

ALTER TABLE activity_events DROP CONSTRAINT chk_activity_events_type;
ALTER TABLE activity_events ADD CONSTRAINT chk_activity_events_type CHECK (event_type IN (
    'TASK_CREATED',
    'TASK_UPDATED',
    'TASK_STATUS_CHANGED',
    'TASK_COMPLETED',
    'TASK_DELETED',
    'WORKFLOW_SUCCEEDED',
    'WORKFLOW_FAILED',
    'TASK_COMMENTED',
    'TASK_DOCUMENT_LINKED'
));
