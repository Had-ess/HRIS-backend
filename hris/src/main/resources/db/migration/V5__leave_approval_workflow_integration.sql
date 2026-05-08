ALTER TABLE leave_types
    ADD COLUMN IF NOT EXISTS validation_workflow_id UUID;

ALTER TABLE leave_types
    ADD CONSTRAINT fk_leave_types_validation_workflow_id
        FOREIGN KEY (validation_workflow_id) REFERENCES validation_workflows(id);

ALTER TABLE validation_workflows
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_validation_workflows_usage_active_default
    ON validation_workflows(usage, is_active, is_default);

ALTER TABLE approval_workflows
    ADD COLUMN IF NOT EXISTS workflow_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS validation_mode VARCHAR(50),
    ADD COLUMN IF NOT EXISTS required_approvals INTEGER,
    ADD COLUMN IF NOT EXISTS routing_snapshot TEXT;
