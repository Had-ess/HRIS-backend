-- Reference, RBAC, and reusable lookup seed data for the HRIS demo.
-- Safe to run after Flyway migrations.

BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Ensure the role hierarchy expected by the current business model exists.
INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'EMPLOYEE', 'Employee', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'EMPLOYEE');

INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'DEPT_MANAGER', 'Department Manager', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'DEPT_MANAGER');

INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'PROJECT_SUPERVISOR', 'Project Supervisor', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'PROJECT_SUPERVISOR');

INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'HR_ADMIN', 'HR Administrator', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'HR_ADMIN');

INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'DIRECTOR', 'Director', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'DIRECTOR');

INSERT INTO roles (code, name, is_system_role, is_active, parent_id)
SELECT 'ADMINISTRATION', 'Administration', TRUE, TRUE, NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'ADMINISTRATION');

UPDATE roles hr
SET parent_id = admin.id
FROM roles admin
WHERE hr.code = 'HR_ADMIN'
  AND admin.code = 'ADMINISTRATION'
  AND hr.parent_id IS DISTINCT FROM admin.id;

-- Reference work schedules used by seeded employees.
INSERT INTO work_schedules (id, name, working_days, hours_per_day)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'Standard 40h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 8),
    ('11111111-1111-1111-1111-111111111102', 'Support 35h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 7)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    working_days = EXCLUDED.working_days,
    hours_per_day = EXCLUDED.hours_per_day;

-- Current-year public holidays used by work-day calculations.
INSERT INTO public_holidays (id, date, name, is_recurring)
VALUES
    (
        '11111111-1111-1111-1111-111111111201',
        make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 1, 1),
        'New Year''s Day',
        TRUE
    ),
    (
        '11111111-1111-1111-1111-111111111202',
        make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 1),
        'Labour Day',
        TRUE
    ),
    (
        '11111111-1111-1111-1111-111111111203',
        make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 12, 17),
        'Revolution Day',
        TRUE
    )
ON CONFLICT (id) DO UPDATE
SET date = EXCLUDED.date,
    name = EXCLUDED.name,
    is_recurring = EXCLUDED.is_recurring;

-- Keep reference leave types aligned with the codebase enum literals.
INSERT INTO leave_types (code, name, is_paid, requires_justification, is_active)
VALUES
    ('ANNUAL', 'Annual Leave', TRUE, FALSE, TRUE),
    ('SICK', 'Sick Leave', TRUE, TRUE, TRUE),
    ('MATERNITY', 'Maternity Leave', TRUE, TRUE, TRUE),
    ('PATERNITY', 'Paternity Leave', TRUE, TRUE, TRUE),
    ('EXCEPTIONAL', 'Exceptional Leave', TRUE, TRUE, TRUE),
    ('UNPAID', 'Unpaid Leave', FALSE, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_paid = EXCLUDED.is_paid,
    requires_justification = EXCLUDED.requires_justification,
    is_active = EXCLUDED.is_active;

-- Admin request types used by the seeded administrative workflows.
INSERT INTO admin_request_types (code, name, is_active)
VALUES
    ('CERT_WORK', 'Work Certificate', TRUE),
    ('CERT_SALARY', 'Salary Certificate', TRUE),
    ('INFO_UPDATE', 'Personal Information Update', TRUE),
    ('EQUIPMENT', 'Equipment Request', TRUE),
    ('OTHER', 'Other', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

-- Explicit permissions used by protected controllers.
INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555101', 'USER_READ', 'USER', 'READ', 'GLOBAL', 'View user role assignments', TRUE),
    ('55555555-5555-5555-5555-555555555102', 'USER_ASSIGN_ROLE', 'USER', 'ASSIGN_ROLE', 'GLOBAL', 'Assign or remove roles from users', TRUE),
    ('55555555-5555-5555-5555-555555555103', 'ROLE_CREATE', 'ROLE', 'CREATE', 'GLOBAL', 'Create roles', TRUE),
    ('55555555-5555-5555-5555-555555555104', 'ROLE_UPDATE', 'ROLE', 'UPDATE', 'GLOBAL', 'Update roles', TRUE),
    ('55555555-5555-5555-5555-555555555105', 'ROLE_DELETE', 'ROLE', 'DELETE', 'GLOBAL', 'Deactivate roles', TRUE),
    ('55555555-5555-5555-5555-555555555106', 'ROLE_READ', 'ROLE', 'READ', 'GLOBAL', 'View role permissions', TRUE),
    ('55555555-5555-5555-5555-555555555107', 'ROLE_ASSIGN_PERMISSION', 'ROLE', 'ASSIGN_PERMISSION', 'GLOBAL', 'Assign permissions to roles', TRUE),
    ('55555555-5555-5555-5555-555555555108', 'PERMISSION_READ', 'PERMISSION', 'READ', 'GLOBAL', 'View permissions', TRUE),
    ('55555555-5555-5555-5555-555555555109', 'PERMISSION_CREATE', 'PERMISSION', 'CREATE', 'GLOBAL', 'Create permissions', TRUE),
    ('55555555-5555-5555-5555-555555555110', 'PERMISSION_UPDATE', 'PERMISSION', 'UPDATE', 'GLOBAL', 'Update permissions', TRUE),
    ('55555555-5555-5555-5555-555555555111', 'PERMISSION_DELETE', 'PERMISSION', 'DELETE', 'GLOBAL', 'Delete permissions', TRUE),
    ('55555555-5555-5555-5555-555555555112', 'DEPARTMENT_CREATE', 'DEPARTMENT', 'CREATE', 'GLOBAL', 'Create departments', TRUE),
    ('55555555-5555-5555-5555-555555555113', 'DEPARTMENT_UPDATE', 'DEPARTMENT', 'UPDATE', 'GLOBAL', 'Update departments', TRUE),
    ('55555555-5555-5555-5555-555555555114', 'DEPARTMENT_DELETE', 'DEPARTMENT', 'DELETE', 'GLOBAL', 'Delete departments', TRUE),
    ('55555555-5555-5555-5555-555555555115', 'DEPARTMENT_DEACTIVATE', 'DEPARTMENT', 'DEACTIVATE', 'GLOBAL', 'Deactivate departments', TRUE),
    ('55555555-5555-5555-5555-555555555116', 'PROJECT_UPDATE', 'PROJECT', 'UPDATE', 'GLOBAL', 'Manage projects, assignments, and department links', TRUE),
    ('55555555-5555-5555-5555-555555555117', 'DASHBOARD_HR_VIEW', 'DASHBOARD', 'HR_VIEW', 'GLOBAL', 'Open the HR dashboard', TRUE),
    ('55555555-5555-5555-5555-555555555118', 'DASHBOARD_DIRECTOR_VIEW', 'DASHBOARD', 'DIRECTOR_VIEW', 'GLOBAL', 'Open the director dashboard', TRUE),
    ('55555555-5555-5555-5555-555555555119', 'ADMIN_REQUEST_PROCESS', 'ADMIN_REQUEST', 'PROCESS', 'GLOBAL', 'Process administrative requests', TRUE),
    ('55555555-5555-5555-5555-555555555120', 'ADMIN_REQUEST_REJECT', 'ADMIN_REQUEST', 'REJECT', 'GLOBAL', 'Reject administrative requests', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

COMMIT;
