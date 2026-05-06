CREATE TABLE project_teams (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    department_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    supervisor_employee_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_project_teams_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_teams_department_id FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_project_teams_supervisor_employee_id FOREIGN KEY (supervisor_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_project_teams_project_id ON project_teams(project_id);
CREATE INDEX idx_project_teams_department_id ON project_teams(department_id);
CREATE INDEX idx_project_teams_supervisor_employee_id ON project_teams(supervisor_employee_id);

ALTER TABLE project_assignments
    ADD COLUMN team_id UUID NULL;

ALTER TABLE project_assignments
    ADD CONSTRAINT fk_project_assignments_team_id FOREIGN KEY (team_id) REFERENCES project_teams(id);

CREATE INDEX idx_project_assignments_team_id ON project_assignments(team_id);
