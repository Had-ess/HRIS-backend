-- HRIS Schema Initialization
-- PostgreSQL 16
-- All FK constraints: fk_tablename_columnname
-- All unique constraints: uq_tablename_columnname
-- All PKs: UUID DEFAULT gen_random_uuid()

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- AUTH MODULE
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    locale_preference VARCHAR(10) NOT NULL DEFAULT 'fr',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_keycloak_id UNIQUE (keycloak_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_users_email ON users(email);

-- ============================================================

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

-- ============================================================

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    parent_id UUID,
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT fk_roles_parent_id FOREIGN KEY (parent_id) REFERENCES roles(id)
);

CREATE INDEX idx_roles_code ON roles(code);
CREATE INDEX idx_roles_parent_id ON roles(parent_id);

-- ============================================================

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    CONSTRAINT uq_permissions_resource_action_scope UNIQUE (resource, action, scope)
);

CREATE INDEX idx_permissions_resource ON permissions(resource);

-- ============================================================

CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    department_id UUID,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_user_roles_department_id FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_roles_department_id ON user_roles(department_id);

-- ============================================================

CREATE TABLE role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    granted_by_id UUID NOT NULL,
    CONSTRAINT fk_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT fk_role_permissions_granted_by_id FOREIGN KEY (granted_by_id) REFERENCES users(id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ============================================================
-- ORGANISATION MODULE
-- ============================================================

CREATE TABLE work_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    working_days VARCHAR(255) NOT NULL,
    hours_per_day INTEGER NOT NULL
);

-- ============================================================

CREATE TABLE public_holidays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_public_holidays_date ON public_holidays(date);

-- ============================================================

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    employee_code VARCHAR(50) NOT NULL,
    hire_date DATE NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    department_id UUID NOT NULL,
    work_schedule_id UUID NOT NULL,
    CONSTRAINT uq_employees_user_id UNIQUE (user_id),
    CONSTRAINT uq_employees_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_employees_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_employees_department_id FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_employees_work_schedule_id FOREIGN KEY (work_schedule_id) REFERENCES work_schedules(id)
);

CREATE INDEX idx_employees_user_id ON employees(user_id);
CREATE INDEX idx_employees_department_id ON employees(department_id);
CREATE INDEX idx_employees_status ON employees(status);

-- Add FK from departments.head_id to employees.id (circular dependency resolved)
ALTER TABLE departments
ADD CONSTRAINT fk_departments_head_employee_id FOREIGN KEY (head_employee_id) REFERENCES employees(id);

-- ============================================================

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    CONSTRAINT uq_projects_code UNIQUE (code)
);

CREATE INDEX idx_projects_code ON projects(code);
CREATE INDEX idx_projects_status ON projects(status);

-- ============================================================

CREATE TABLE project_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    project_id UUID NOT NULL,
    supervisor_id UUID NOT NULL,
    assignment_role VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_project_assignments_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_project_assignments_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_assignments_supervisor_id FOREIGN KEY (supervisor_id) REFERENCES employees(id)
);

CREATE INDEX idx_project_assignments_employee_id ON project_assignments(employee_id);
CREATE INDEX idx_project_assignments_project_id ON project_assignments(project_id);
CREATE INDEX idx_project_assignments_supervisor_id ON project_assignments(supervisor_id);
CREATE INDEX idx_project_assignments_date_range ON project_assignments(employee_id, start_date, end_date, is_active);

-- ============================================================

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

-- ============================================================
-- APPROVAL MODULE
-- ============================================================

CREATE TABLE approval_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(50) NOT NULL,
    subject_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_approval_workflows_subject ON approval_workflows(subject_type, subject_id);
CREATE INDEX idx_approval_workflows_status ON approval_workflows(status);

-- ============================================================

CREATE TABLE approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL,
    approver_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    context VARCHAR(50) NOT NULL,
    routing_snapshot TEXT NOT NULL,
    comment TEXT,
    decided_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_approval_steps_workflow_id FOREIGN KEY (workflow_id) REFERENCES approval_workflows(id),
    CONSTRAINT fk_approval_steps_approver_id FOREIGN KEY (approver_id) REFERENCES users(id)
);

CREATE INDEX idx_approval_steps_workflow_id ON approval_steps(workflow_id);
CREATE INDEX idx_approval_steps_approver_id ON approval_steps(approver_id);
CREATE INDEX idx_approval_steps_status ON approval_steps(status);

-- ============================================================
-- LEAVE MODULE
-- ============================================================

CREATE TABLE leave_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    requires_justification BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_leave_types_code UNIQUE (code)
);

-- ============================================================

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

-- ============================================================

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
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_leave_requests_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_requests_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id)
);

CREATE INDEX idx_leave_requests_employee_id ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);
CREATE INDEX idx_leave_requests_submitted_at ON leave_requests(submitted_at);

-- ============================================================

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

-- ============================================================

CREATE TABLE file_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    uploaded_by_id UUID NOT NULL,
    CONSTRAINT fk_file_attachments_request_id FOREIGN KEY (request_id) REFERENCES leave_requests(id),
    CONSTRAINT fk_file_attachments_uploaded_by_id FOREIGN KEY (uploaded_by_id) REFERENCES users(id)
);

CREATE INDEX idx_file_attachments_request_id ON file_attachments(request_id);

-- ============================================================
-- ADMIN MODULE
-- ============================================================

CREATE TABLE admin_request_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_admin_request_types_code UNIQUE (code)
);

-- ============================================================

CREATE TABLE admin_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL,
    request_type_id UUID NOT NULL,
    tracking_number VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    urgency_level VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    metadata TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_id UUID,
    CONSTRAINT uq_admin_requests_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_admin_requests_requester_id FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_admin_requests_request_type_id FOREIGN KEY (request_type_id) REFERENCES admin_request_types(id),
    CONSTRAINT fk_admin_requests_resolved_by_id FOREIGN KEY (resolved_by_id) REFERENCES users(id)
);

CREATE INDEX idx_admin_requests_requester_id ON admin_requests(requester_id);
CREATE INDEX idx_admin_requests_status ON admin_requests(status);
CREATE INDEX idx_admin_requests_tracking_number ON admin_requests(tracking_number);

-- ============================================================
-- NOTIFICATION MODULE
-- ============================================================

CREATE TABLE notification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    target_user_id UUID NOT NULL,
    title_key VARCHAR(255) NOT NULL,
    body_key VARCHAR(255) NOT NULL,
    params TEXT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_events_target_user_id FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_events_target_user_id ON notification_events(target_user_id);
CREATE INDEX idx_notification_events_published_at ON notification_events(published_at);

-- ============================================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- ============================================================
-- ANALYTICS MODULE
-- ============================================================

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID,
    action VARCHAR(50) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id UUID,
    previous_state TEXT,
    new_state TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_logs_actor_id FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource, resource_id);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

-- ============================================================

CREATE TABLE analytics_dashboards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    viewer_role_code VARCHAR(50) NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    scope_entity_id UUID,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_dashboards_viewer_role ON analytics_dashboards(viewer_role_code);

-- ============================================================

CREATE TABLE leave_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period VARCHAR(20) NOT NULL,
    department_id UUID NOT NULL,
    total_requests INTEGER NOT NULL,
    approved_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    avg_processing_days DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_leave_metrics_department_id FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_leave_metrics_period ON leave_metrics(period);
CREATE INDEX idx_leave_metrics_department_id ON leave_metrics(department_id);

-- ============================================================

CREATE TABLE headcount_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date DATE NOT NULL,
    department_id UUID NOT NULL,
    total_employees INTEGER NOT NULL,
    active_employees INTEGER NOT NULL,
    new_hires_this_month INTEGER NOT NULL,
    departures_this_month INTEGER NOT NULL,
    CONSTRAINT fk_headcount_metrics_department_id FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_headcount_metrics_snapshot_date ON headcount_metrics(snapshot_date);
CREATE INDEX idx_headcount_metrics_department_id ON headcount_metrics(department_id);

-- ============================================================

CREATE TABLE absence_impact_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    report_date DATE NOT NULL,
    total_absence_days INTEGER NOT NULL,
    affected_members INTEGER NOT NULL,
    estimated_delay_days INTEGER NOT NULL,
    risk_level VARCHAR(50) NOT NULL,
    CONSTRAINT fk_absence_impact_reports_project_id FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_absence_impact_reports_project_id ON absence_impact_reports(project_id);
CREATE INDEX idx_absence_impact_reports_report_date ON absence_impact_reports(report_date);

-- ============================================================

CREATE TABLE export_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exported_by_id UUID NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    format VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    exported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_export_records_exported_by_id FOREIGN KEY (exported_by_id) REFERENCES users(id)
);

CREATE INDEX idx_export_records_exported_by_id ON export_records(exported_by_id);
CREATE INDEX idx_export_records_exported_at ON export_records(exported_at);

-- ============================================================
-- SEED DATA
-- ============================================================

-- Default work schedule (Monday-Friday, 8h/day)
INSERT INTO work_schedules (id, name, working_days, hours_per_day)
VALUES (gen_random_uuid(), 'Standard 40h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 8);

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_active)
VALUES
    (gen_random_uuid(), 'seed-employee', 'employee@hris.local', 'Seed', 'Employee', 'fr', TRUE),
    (gen_random_uuid(), 'seed-manager', 'manager@hris.local', 'Seed', 'Manager', 'fr', TRUE),
    (gen_random_uuid(), 'seed-hr-admin', 'hradmin@hris.local', 'Seed', 'HrAdmin', 'fr', TRUE)
ON CONFLICT (email) DO NOTHING;

-- System roles
INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
VALUES
    ('EMPLOYEE', 'Employee', TRUE, TRUE, NULL),
    ('DEPT_MANAGER', 'Department Manager', TRUE, TRUE, NULL),
    ('PROJECT_SUPERVISOR', 'Project Supervisor', TRUE, TRUE, NULL),
    ('HR_ADMIN', 'HR Administrator', TRUE, TRUE, NULL),
    ('DIRECTOR', 'Director', TRUE, TRUE, NULL);

-- Default leave types
INSERT INTO leave_types (code, name, is_paid, requires_justification, is_active)
VALUES
    ('ANNUAL', 'Annual Leave', TRUE, FALSE, TRUE),
    ('SICK', 'Sick Leave', TRUE, TRUE, TRUE),
    ('MATERNITY', 'Maternity Leave', TRUE, TRUE, TRUE),
    ('PATERNITY', 'Paternity Leave', TRUE, TRUE, TRUE),
    ('EXCEPTIONAL', 'Exceptional Leave', TRUE, TRUE, TRUE),
    ('UNPAID', 'Unpaid Leave', FALSE, TRUE, TRUE);

-- Default admin request types
INSERT INTO admin_request_types (code, name, is_active)
VALUES
    ('CERT_WORK', 'Work Certificate', TRUE),
    ('CERT_SALARY', 'Salary Certificate', TRUE),
    ('INFO_UPDATE', 'Personal Information Update', TRUE),
    ('EQUIPMENT', 'Equipment Request', TRUE),
    ('OTHER', 'Other', TRUE);

-- ============================================================
-- END OF MIGRATION
-- ============================================================
