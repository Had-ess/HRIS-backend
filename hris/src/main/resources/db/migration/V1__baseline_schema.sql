CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    locale_preference VARCHAR(10) NOT NULL DEFAULT 'fr',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login TIMESTAMPTZ,
    CONSTRAINT uq_users_keycloak_id UNIQUE (keycloak_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_permissions_name UNIQUE (name),
    CONSTRAINT uq_permissions_resource_action_scope UNIQUE (resource, action, scope)
);

CREATE INDEX idx_permissions_resource ON permissions(resource);

CREATE TABLE access_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    display_key VARCHAR(150) NOT NULL,
    description_key VARCHAR(150),
    is_system_profile BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_access_profiles_code UNIQUE (code)
);

CREATE TABLE menu_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL,
    translation_key VARCHAR(150) NOT NULL,
    section_code VARCHAR(80) NOT NULL,
    route VARCHAR(255) NOT NULL,
    icon VARCHAR(80) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_menu_items_code UNIQUE (code),
    CONSTRAINT uq_menu_items_translation_key UNIQUE (translation_key)
);

CREATE INDEX idx_menu_items_section_order ON menu_items(section_code, display_order);

CREATE TABLE profile_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by_id UUID,
    CONSTRAINT uq_profile_permissions UNIQUE (profile_id, permission_id),
    CONSTRAINT fk_profile_permissions_profile_id FOREIGN KEY (profile_id) REFERENCES access_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_permissions_granted_by_id FOREIGN KEY (granted_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_profile_permissions_profile_id ON profile_permissions(profile_id);
CREATE INDEX idx_profile_permissions_permission_id ON profile_permissions(permission_id);

CREATE TABLE profile_menu_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by_id UUID,
    CONSTRAINT uq_profile_menu_access UNIQUE (profile_id, menu_item_id),
    CONSTRAINT fk_profile_menu_access_profile_id FOREIGN KEY (profile_id) REFERENCES access_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_menu_access_menu_item_id FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_menu_access_granted_by_id FOREIGN KEY (granted_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_profile_menu_access_profile_id ON profile_menu_access(profile_id);
CREATE INDEX idx_profile_menu_access_menu_item_id ON profile_menu_access(menu_item_id);

CREATE TABLE user_profile_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    assigned_by_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_user_profile_assignments UNIQUE (user_id, profile_id, assigned_at),
    CONSTRAINT fk_user_profile_assignments_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_assignments_profile_id FOREIGN KEY (profile_id) REFERENCES access_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_assignments_assigned_by_id FOREIGN KEY (assigned_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_profile_assignments_user_id ON user_profile_assignments(user_id);
CREATE INDEX idx_user_profile_assignments_profile_id ON user_profile_assignments(profile_id);

CREATE TABLE work_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    working_days VARCHAR(255) NOT NULL,
    hours_per_day INTEGER NOT NULL
);

CREATE TABLE public_holidays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_public_holidays_date ON public_holidays(date);

CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    head_employee_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_departments_code UNIQUE (code)
);

CREATE INDEX idx_departments_code ON departments(code);
CREATE INDEX idx_departments_head_employee_id ON departments(head_employee_id);

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    employee_code VARCHAR(50) NOT NULL,
    hire_date DATE NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    department_id UUID NOT NULL,
    supervisor_employee_id UUID,
    termination_date DATE,
    work_schedule_id UUID NOT NULL,
    CONSTRAINT uq_employees_user_id UNIQUE (user_id),
    CONSTRAINT uq_employees_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_employees_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_employees_department_id FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_employees_supervisor_employee_id FOREIGN KEY (supervisor_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employees_work_schedule_id FOREIGN KEY (work_schedule_id) REFERENCES work_schedules(id)
);

CREATE INDEX idx_employees_user_id ON employees(user_id);
CREATE INDEX idx_employees_department_id ON employees(department_id);
CREATE INDEX idx_employees_status ON employees(status);
CREATE INDEX idx_employees_supervisor_employee_id ON employees(supervisor_employee_id);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head_employee_id
        FOREIGN KEY (head_employee_id) REFERENCES employees(id);

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    project_manager_employee_id UUID,
    CONSTRAINT uq_projects_code UNIQUE (code),
    CONSTRAINT fk_projects_project_manager_employee_id FOREIGN KEY (project_manager_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_projects_code ON projects(code);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_project_manager_employee_id ON projects(project_manager_employee_id);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    department_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    supervisor_employee_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_teams_code UNIQUE (code),
    CONSTRAINT fk_teams_department_id FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_teams_supervisor_employee_id FOREIGN KEY (supervisor_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_teams_department_id ON teams(department_id);
CREATE INDEX idx_teams_supervisor_employee_id ON teams(supervisor_employee_id);

CREATE TABLE team_project_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL,
    project_id UUID NOT NULL,
    start_date DATE,
    end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_team_project_links_team_project UNIQUE (team_id, project_id),
    CONSTRAINT fk_team_project_links_team_id FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_team_project_links_project_id FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_team_project_links_team_id ON team_project_links(team_id);
CREATE INDEX idx_team_project_links_project_id ON team_project_links(project_id);

CREATE TABLE project_departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    department_id UUID NOT NULL,
    is_lead BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_project_departments_project_dept UNIQUE (project_id, department_id),
    CONSTRAINT fk_project_departments_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_departments_department_id FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_project_departments_project_id ON project_departments(project_id);
CREATE INDEX idx_project_departments_department_id ON project_departments(department_id);

CREATE TABLE project_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    project_id UUID NOT NULL,
    team_id UUID,
    supervisor_id UUID NOT NULL,
    assignment_role VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_project_assignments_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_project_assignments_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_assignments_team_id FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_project_assignments_supervisor_id FOREIGN KEY (supervisor_id) REFERENCES employees(id)
);

CREATE INDEX idx_project_assignments_employee_id ON project_assignments(employee_id);
CREATE INDEX idx_project_assignments_project_id ON project_assignments(project_id);
CREATE INDEX idx_project_assignments_team_id ON project_assignments(team_id);
CREATE INDEX idx_project_assignments_supervisor_id ON project_assignments(supervisor_id);
CREATE INDEX idx_project_assignments_date_range ON project_assignments(employee_id, start_date, end_date, is_active);

CREATE TABLE approval_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(50) NOT NULL,
    subject_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_approval_workflows_subject ON approval_workflows(subject_type, subject_id);
CREATE INDEX idx_approval_workflows_status ON approval_workflows(status);

CREATE TABLE approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL,
    approver_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    context VARCHAR(50) NOT NULL,
    source_type VARCHAR(50),
    approver_level INTEGER,
    routing_snapshot TEXT NOT NULL,
    comment TEXT,
    decided_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_approval_steps_workflow_id FOREIGN KEY (workflow_id) REFERENCES approval_workflows(id),
    CONSTRAINT fk_approval_steps_approver_id FOREIGN KEY (approver_id) REFERENCES users(id)
);

CREATE INDEX idx_approval_steps_workflow_id ON approval_steps(workflow_id);
CREATE INDEX idx_approval_steps_approver_id ON approval_steps(approver_id);
CREATE INDEX idx_approval_steps_status ON approval_steps(status);

CREATE TABLE leave_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    requires_justification BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_leave_types_code UNIQUE (code)
);

CREATE TABLE leave_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_type_id UUID NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    min_seniority_years INTEGER NOT NULL,
    max_days_per_year INTEGER NOT NULL,
    carry_over_days INTEGER NOT NULL,
    carry_over_expiry INTEGER NOT NULL,
    CONSTRAINT uq_leave_policies_type_contract_seniority UNIQUE (leave_type_id, contract_type, min_seniority_years),
    CONSTRAINT fk_leave_policies_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id)
);

CREATE INDEX idx_leave_policies_leave_type_id ON leave_policies(leave_type_id);

CREATE TABLE leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    leave_type_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    working_days INTEGER NOT NULL,
    urgency_level VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_leave_requests_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_requests_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id)
);

CREATE INDEX idx_leave_requests_employee_id ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);
CREATE INDEX idx_leave_requests_submitted_at ON leave_requests(submitted_at);

CREATE TABLE leave_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    leave_type_id UUID NOT NULL,
    year INTEGER NOT NULL,
    total_days INTEGER NOT NULL,
    used_days INTEGER NOT NULL DEFAULT 0,
    pending_days INTEGER NOT NULL DEFAULT 0,
    carry_over_days INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_leave_balances_employee_type_year UNIQUE (employee_id, leave_type_id, year),
    CONSTRAINT fk_leave_balances_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_balances_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id)
);

CREATE INDEX idx_leave_balances_employee_id ON leave_balances(employee_id);
CREATE INDEX idx_leave_balances_year ON leave_balances(year);

CREATE TABLE file_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    uploaded_by_id UUID NOT NULL,
    CONSTRAINT fk_file_attachments_request_id FOREIGN KEY (request_id) REFERENCES leave_requests(id),
    CONSTRAINT fk_file_attachments_uploaded_by_id FOREIGN KEY (uploaded_by_id) REFERENCES users(id)
);

CREATE INDEX idx_file_attachments_request_id ON file_attachments(request_id);

CREATE TABLE admin_request_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_admin_request_types_code UNIQUE (code)
);

CREATE TABLE admin_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL,
    request_type_id UUID NOT NULL,
    tracking_number VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    urgency_level VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    metadata TEXT,
    rejection_reason TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolved_by_id UUID,
    CONSTRAINT uq_admin_requests_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_admin_requests_requester_id FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_admin_requests_request_type_id FOREIGN KEY (request_type_id) REFERENCES admin_request_types(id),
    CONSTRAINT fk_admin_requests_resolved_by_id FOREIGN KEY (resolved_by_id) REFERENCES users(id)
);

CREATE INDEX idx_admin_requests_requester_id ON admin_requests(requester_id);
CREATE INDEX idx_admin_requests_status ON admin_requests(status);
CREATE INDEX idx_admin_requests_tracking_number ON admin_requests(tracking_number);

CREATE TABLE notification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    target_user_id UUID NOT NULL,
    title_key VARCHAR(255) NOT NULL,
    body_key VARCHAR(255) NOT NULL,
    params TEXT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_events_target_user_id FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_events_target_user_id ON notification_events(target_user_id);
CREATE INDEX idx_notification_events_published_at ON notification_events(published_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    link_path VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID,
    action VARCHAR(50) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id UUID,
    previous_state TEXT,
    new_state TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_logs_actor_id FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource, resource_id);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

CREATE TABLE analytics_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_date DATE NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_analytics_events_processed_at ON analytics_events(processed_at);
CREATE INDEX idx_analytics_events_event_date ON analytics_events(event_date);
CREATE INDEX idx_analytics_events_aggregate ON analytics_events(aggregate_type, aggregate_id);

CREATE TABLE analytics_leave_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_date DATE NOT NULL,
    leave_request_id UUID NOT NULL UNIQUE,
    employee_id UUID NOT NULL,
    department_id UUID,
    project_id UUID,
    team_id UUID,
    leave_type_id UUID NOT NULL,
    working_days INTEGER NOT NULL,
    request_status VARCHAR(50) NOT NULL,
    approval_duration_days INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_analytics_leave_facts_event_date ON analytics_leave_facts(event_date);
CREATE INDEX idx_analytics_leave_facts_department_id ON analytics_leave_facts(department_id);
CREATE INDEX idx_analytics_leave_facts_project_id ON analytics_leave_facts(project_id);
CREATE INDEX idx_analytics_leave_facts_team_id ON analytics_leave_facts(team_id);
CREATE INDEX idx_analytics_leave_facts_employee_id ON analytics_leave_facts(employee_id);

CREATE TABLE analytics_approval_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_date DATE NOT NULL,
    workflow_id UUID NOT NULL,
    approval_step_id UUID NOT NULL UNIQUE,
    approver_id UUID NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    approver_level INTEGER NOT NULL,
    step_status VARCHAR(50) NOT NULL,
    decision_delay_days INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_analytics_approval_facts_event_date ON analytics_approval_facts(event_date);
CREATE INDEX idx_analytics_approval_facts_source_level ON analytics_approval_facts(source_type, approver_level);

CREATE TABLE analytics_headcount_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    employee_id UUID NOT NULL,
    department_id UUID,
    team_id UUID,
    employee_status VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL,
    CONSTRAINT uq_analytics_headcount_facts_snapshot_employee UNIQUE (snapshot_date, employee_id)
);

CREATE INDEX idx_analytics_headcount_facts_snapshot_date ON analytics_headcount_facts(snapshot_date);
CREATE INDEX idx_analytics_headcount_facts_department_id ON analytics_headcount_facts(department_id);
CREATE INDEX idx_analytics_headcount_facts_team_id ON analytics_headcount_facts(team_id);

CREATE TABLE analytics_project_absence_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    project_id UUID NOT NULL,
    team_id UUID,
    absent_employees INTEGER NOT NULL,
    absence_days INTEGER NOT NULL,
    affected_members INTEGER NOT NULL,
    estimated_delay_days INTEGER NOT NULL,
    risk_level VARCHAR(50) NOT NULL
);

CREATE INDEX idx_analytics_project_absence_facts_snapshot_date ON analytics_project_absence_facts(snapshot_date);
CREATE INDEX idx_analytics_project_absence_facts_project_id ON analytics_project_absence_facts(project_id);

CREATE TABLE analytics_leave_metrics_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    scope_id UUID,
    total_requests INTEGER NOT NULL,
    approved_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    pending_count INTEGER NOT NULL,
    average_processing_days NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_analytics_leave_metrics_snapshot UNIQUE (snapshot_date, scope_type, scope_id)
);

CREATE INDEX idx_analytics_leave_metrics_snapshots_scope ON analytics_leave_metrics_snapshots(scope_type, scope_id);

CREATE TABLE analytics_headcount_metrics_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    scope_id UUID,
    total_employees INTEGER NOT NULL,
    active_employees INTEGER NOT NULL,
    new_hires INTEGER NOT NULL,
    terminated_employees INTEGER NOT NULL,
    CONSTRAINT uq_analytics_headcount_metrics_snapshot UNIQUE (snapshot_date, scope_type, scope_id)
);

CREATE INDEX idx_analytics_headcount_metrics_snapshots_scope ON analytics_headcount_metrics_snapshots(scope_type, scope_id);

CREATE TABLE analytics_leave_distribution_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    scope_id UUID,
    leave_type_id UUID NOT NULL,
    request_count INTEGER NOT NULL,
    total_days INTEGER NOT NULL,
    CONSTRAINT uq_analytics_leave_distribution_snapshot UNIQUE (snapshot_date, scope_type, scope_id, leave_type_id)
);

CREATE INDEX idx_analytics_leave_distribution_snapshots_scope ON analytics_leave_distribution_snapshots(scope_type, scope_id);

CREATE TABLE analytics_approval_bottleneck_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    scope_id UUID,
    source_type VARCHAR(50) NOT NULL,
    approver_level INTEGER NOT NULL,
    pending_count INTEGER NOT NULL,
    average_decision_days NUMERIC(10, 2) NOT NULL,
    rejection_rate NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_analytics_approval_bottleneck_snapshot UNIQUE (snapshot_date, scope_type, scope_id, source_type, approver_level)
);

CREATE INDEX idx_analytics_approval_bottlenecks_scope
    ON analytics_approval_bottleneck_snapshots(scope_type, scope_id, source_type, approver_level);

CREATE TABLE export_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exported_by_id UUID NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    format VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_export_records_exported_by_id FOREIGN KEY (exported_by_id) REFERENCES users(id)
);

CREATE INDEX idx_export_records_exported_by_id ON export_records(exported_by_id);
CREATE INDEX idx_export_records_exported_at ON export_records(exported_at);

CREATE TABLE employee_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    effective_date DATE NOT NULL,
    reason VARCHAR(255),
    changed_by UUID,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_employee_status_history_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employee_status_history_changed_by FOREIGN KEY (changed_by) REFERENCES users(id)
);

CREATE INDEX idx_employee_status_history_employee_id ON employee_status_history(employee_id);
CREATE INDEX idx_employee_status_history_effective_date ON employee_status_history(effective_date);

CREATE TABLE employee_department_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    previous_department_id UUID,
    new_department_id UUID NOT NULL,
    effective_date DATE NOT NULL,
    changed_by UUID,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_employee_department_history_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employee_department_history_previous_department_id FOREIGN KEY (previous_department_id) REFERENCES departments(id),
    CONSTRAINT fk_employee_department_history_new_department_id FOREIGN KEY (new_department_id) REFERENCES departments(id),
    CONSTRAINT fk_employee_department_history_changed_by FOREIGN KEY (changed_by) REFERENCES users(id)
);

CREATE INDEX idx_employee_department_history_employee_id ON employee_department_history(employee_id);
CREATE INDEX idx_employee_department_history_effective_date ON employee_department_history(effective_date);
