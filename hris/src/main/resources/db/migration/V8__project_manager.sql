ALTER TABLE projects
    ADD COLUMN project_manager_employee_id UUID NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_project_manager_employee_id
        FOREIGN KEY (project_manager_employee_id) REFERENCES employees(id);

CREATE INDEX idx_projects_project_manager_employee_id
    ON projects(project_manager_employee_id);
