INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555301', 'ANALYTICS_READ_OWN', 'ANALYTICS', 'READ_OWN', 'OWN', 'Read own analytics and personal reports', TRUE),
    ('55555555-5555-5555-5555-555555555302', 'ANALYTICS_READ_SCOPED', 'ANALYTICS', 'READ_SCOPED', 'SCOPED', 'Read scoped analytics and team reports', TRUE),
    ('55555555-5555-5555-5555-555555555303', 'ANALYTICS_READ_GLOBAL', 'ANALYTICS', 'READ_GLOBAL', 'GLOBAL', 'Read global analytics and operational reports', TRUE),
    ('55555555-5555-5555-5555-555555555304', 'REPORT_READ', 'REPORT', 'READ', 'SCOPED', 'Open the reports workspace', TRUE),
    ('55555555-5555-5555-5555-555555555305', 'REPORT_EXPORT', 'REPORT', 'EXPORT', 'GLOBAL', 'Export analytics reports', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN ('ANALYTICS_READ_OWN', 'REPORT_READ')
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN ('ANALYTICS_READ_OWN', 'ANALYTICS_READ_SCOPED', 'REPORT_READ')
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN ('ANALYTICS_READ_OWN', 'ANALYTICS_READ_SCOPED', 'ANALYTICS_READ_GLOBAL', 'REPORT_READ', 'REPORT_EXPORT')
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code = 'menu.workspace.reports'
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_leave_requests_status ON leave_requests(status);
CREATE INDEX IF NOT EXISTS idx_leave_requests_leave_type_id ON leave_requests(leave_type_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_employee_id ON leave_requests(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_submitted_at ON leave_requests(submitted_at);

CREATE INDEX IF NOT EXISTS idx_approval_steps_status ON approval_steps(status);
CREATE INDEX IF NOT EXISTS idx_approval_steps_approver_id ON approval_steps(approver_id);
CREATE INDEX IF NOT EXISTS idx_approval_steps_decided_at ON approval_steps(decided_at);

CREATE INDEX IF NOT EXISTS idx_approval_workflows_status ON approval_workflows(status);
CREATE INDEX IF NOT EXISTS idx_approval_workflows_created_at ON approval_workflows(created_at);
CREATE INDEX IF NOT EXISTS idx_approval_workflows_completed_at ON approval_workflows(completed_at);
CREATE INDEX IF NOT EXISTS idx_approval_workflows_subject ON approval_workflows(subject_type, subject_id);

CREATE INDEX IF NOT EXISTS idx_admin_requests_status ON admin_requests(status);
CREATE INDEX IF NOT EXISTS idx_admin_requests_type_id ON admin_requests(type_id);
CREATE INDEX IF NOT EXISTS idx_admin_requests_submitted_at ON admin_requests(submitted_at);
CREATE INDEX IF NOT EXISTS idx_admin_requests_completed_at ON admin_requests(completed_at);

CREATE INDEX IF NOT EXISTS idx_leave_balances_employee_id ON leave_balances(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_balances_leave_type_id ON leave_balances(leave_type_id);
CREATE INDEX IF NOT EXISTS idx_leave_balance_transactions_occurred_at ON leave_balance_transactions(occurred_at);
CREATE INDEX IF NOT EXISTS idx_leave_balance_transactions_employee_type ON leave_balance_transactions(employee_id, leave_type_id);

CREATE INDEX IF NOT EXISTS idx_employees_department_id ON employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employees_supervisor_employee_id ON employees(supervisor_employee_id);
CREATE INDEX IF NOT EXISTS idx_project_assignments_team_active_dates ON project_assignments(team_id, is_active, start_date, end_date);
