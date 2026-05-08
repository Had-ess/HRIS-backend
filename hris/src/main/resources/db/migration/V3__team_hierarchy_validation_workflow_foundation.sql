BEGIN;

CREATE TABLE team_hierarchy_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL,
    responsible_employee_id UUID,
    collaborator_employee_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_team_hierarchy_relations_team_id FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_team_hierarchy_relations_responsible_employee_id FOREIGN KEY (responsible_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_team_hierarchy_relations_collaborator_employee_id FOREIGN KEY (collaborator_employee_id) REFERENCES employees(id),
    CONSTRAINT chk_team_hierarchy_dates CHECK (end_date IS NULL OR start_date <= end_date)
);

CREATE INDEX idx_team_hierarchy_relations_team_id ON team_hierarchy_relations(team_id);
CREATE INDEX idx_team_hierarchy_relations_collaborator_id ON team_hierarchy_relations(collaborator_employee_id);
CREATE INDEX idx_team_hierarchy_relations_responsible_id ON team_hierarchy_relations(responsible_employee_id);
CREATE INDEX idx_team_hierarchy_relations_status_dates ON team_hierarchy_relations(team_id, status, start_date, end_date);

CREATE TABLE validation_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    usage VARCHAR(50) NOT NULL,
    validator_source VARCHAR(50) NOT NULL,
    validation_mode VARCHAR(50) NOT NULL,
    min_validators INTEGER,
    fallback_mode VARCHAR(50) NOT NULL,
    fallback_profile_id UUID,
    fallback_permission_code VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_validation_workflows_code UNIQUE (code),
    CONSTRAINT fk_validation_workflows_fallback_profile_id FOREIGN KEY (fallback_profile_id) REFERENCES access_profiles(id),
    CONSTRAINT fk_validation_workflows_fallback_permission_code FOREIGN KEY (fallback_permission_code) REFERENCES permissions(name)
);

CREATE INDEX idx_validation_workflows_usage ON validation_workflows(usage, is_active);
CREATE INDEX idx_validation_workflows_code ON validation_workflows(code);

COMMIT;
