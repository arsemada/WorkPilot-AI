ALTER TABLE tasks ADD COLUMN recurrence VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE tasks ADD COLUMN spawned_from_id UUID REFERENCES tasks(id);

ALTER TABLE tasks ADD CONSTRAINT chk_tasks_recurrence CHECK (recurrence IN ('NONE', 'WEEKLY', 'MONTHLY'));

CREATE INDEX idx_tasks_spawned_from_id ON tasks(spawned_from_id);
