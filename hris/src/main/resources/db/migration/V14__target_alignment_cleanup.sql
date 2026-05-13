ALTER TABLE projects
    DROP CONSTRAINT IF EXISTS fk_projects_project_manager_employee_id;

DROP INDEX IF EXISTS idx_projects_project_manager_employee_id;

ALTER TABLE projects
    DROP COLUMN IF EXISTS project_manager_employee_id;

UPDATE approval_steps
SET context = 'TEAM'
WHERE context = 'PROJECT';

UPDATE approval_steps
SET source_type = 'TEAM_CHAIN'
WHERE source_type IN ('PRIMARY_CHAIN', 'PROJECT_CHAIN');

UPDATE analytics_approval_facts
SET source_type = 'TEAM_CHAIN'
WHERE source_type IN ('PRIMARY_CHAIN', 'PROJECT_CHAIN');

UPDATE analytics_approval_bottleneck_snapshots
SET source_type = 'TEAM_CHAIN'
WHERE source_type IN ('PRIMARY_CHAIN', 'PROJECT_CHAIN');
