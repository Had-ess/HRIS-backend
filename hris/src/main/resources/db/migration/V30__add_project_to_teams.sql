-- Add nullable column
ALTER TABLE teams ADD COLUMN project_id UUID;

-- Backfill from active links in team_project_links
UPDATE teams t
SET project_id = (
    SELECT project_id 
    FROM team_project_links tpl 
    WHERE tpl.team_id = t.id AND tpl.is_active = TRUE
    LIMIT 1
);

-- Fallback to seeded default Atlas project for any team without an active link
UPDATE teams
SET project_id = '77777777-7777-7777-7777-777777777404'
WHERE project_id IS NULL;

-- Apply non-null constraints & foreign key relation
ALTER TABLE teams ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE teams ADD CONSTRAINT fk_teams_project_id FOREIGN KEY (project_id) REFERENCES projects(id);
