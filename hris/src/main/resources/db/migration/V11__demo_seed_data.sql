-- Demo data for disposable local development databases.
-- This keeps the seeded Keycloak users aligned with local HRIS entities
-- so login-required frontend flows have employee and profile data available.

BEGIN;

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333301', 'KC_DEMO_ADMIN', 'admin@demo.hris.local', 'Nadia', 'Ben Salem', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333302', 'KC_DEMO_HR', 'hr.admin@demo.hris.local', 'Sami', 'Khadhraoui', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333303', 'KC_DEMO_MANAGER', 'manager.engineering@demo.hris.local', 'Karim', 'Jlassi', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333304', 'KC_DEMO_DEVELOPER', 'developer@demo.hris.local', 'Yasmine', 'Trabelsi', 'en', TRUE)
ON CONFLICT (email) DO UPDATE
SET keycloak_id = EXCLUDED.keycloak_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    locale_preference = EXCLUDED.locale_preference,
    is_active = EXCLUDED.is_active;

INSERT INTO departments (id, name, code, head_employee_id, is_active)
VALUES
    ('44444444-4444-4444-4444-444444444401', 'Engineering', 'ENG', NULL, TRUE),
    ('44444444-4444-4444-4444-444444444402', 'Operations', 'OPS', NULL, TRUE)
ON CONFLICT (code) DO NOTHING;

UPDATE employees
SET employee_code = 'EMP-ADMIN',
    hire_date = CURRENT_DATE - INTERVAL '720 days',
    job_title = 'System Administrator',
    status = 'ACTIVE',
    contract_type = 'PERMANENT',
    department_id = (SELECT id FROM departments WHERE code = 'ENG'),
    supervisor_employee_id = NULL,
    termination_date = NULL,
    work_schedule_id = (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
WHERE user_id = (SELECT id FROM users WHERE email = 'admin@demo.hris.local');

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
SELECT
    '55555555-5555-5555-5555-555555555401',
    u.id,
    'EMP-ADMIN',
    CURRENT_DATE - INTERVAL '720 days',
    'System Administrator',
    'ACTIVE',
    'PERMANENT',
    (SELECT id FROM departments WHERE code = 'ENG'),
    NULL,
    NULL,
    (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
FROM users u
WHERE u.email = 'admin@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1
      FROM employees existing
      WHERE existing.user_id = u.id
  );

UPDATE employees
SET employee_code = 'EMP-HR',
    hire_date = CURRENT_DATE - INTERVAL '540 days',
    job_title = 'HR Operations Lead',
    status = 'ACTIVE',
    contract_type = 'PERMANENT',
    department_id = (SELECT id FROM departments WHERE code = 'OPS'),
    supervisor_employee_id = NULL,
    termination_date = NULL,
    work_schedule_id = (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
WHERE user_id = (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local');

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
SELECT
    '55555555-5555-5555-5555-555555555402',
    u.id,
    'EMP-HR',
    CURRENT_DATE - INTERVAL '540 days',
    'HR Operations Lead',
    'ACTIVE',
    'PERMANENT',
    (SELECT id FROM departments WHERE code = 'OPS'),
    NULL,
    NULL,
    (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
FROM users u
WHERE u.email = 'hr.admin@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1
      FROM employees existing
      WHERE existing.user_id = u.id
  );

UPDATE employees
SET employee_code = 'EMP-MGR',
    hire_date = CURRENT_DATE - INTERVAL '365 days',
    job_title = 'Engineering Manager',
    status = 'ACTIVE',
    contract_type = 'PERMANENT',
    department_id = (SELECT id FROM departments WHERE code = 'ENG'),
    supervisor_employee_id = NULL,
    termination_date = NULL,
    work_schedule_id = (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
WHERE user_id = (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local');

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
SELECT
    '55555555-5555-5555-5555-555555555403',
    u.id,
    'EMP-MGR',
    CURRENT_DATE - INTERVAL '365 days',
    'Engineering Manager',
    'ACTIVE',
    'PERMANENT',
    (SELECT id FROM departments WHERE code = 'ENG'),
    NULL,
    NULL,
    (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
FROM users u
WHERE u.email = 'manager.engineering@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1
      FROM employees existing
      WHERE existing.user_id = u.id
  );

UPDATE employees
SET employee_code = 'EMP-001',
    hire_date = CURRENT_DATE - INTERVAL '180 days',
    job_title = 'Software Engineer',
    status = 'ACTIVE',
    contract_type = 'PERMANENT',
    department_id = (SELECT id FROM departments WHERE code = 'ENG'),
    supervisor_employee_id = (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'),
    termination_date = NULL,
    work_schedule_id = (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
WHERE user_id = (SELECT id FROM users WHERE email = 'developer@demo.hris.local');

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
SELECT
    '55555555-5555-5555-5555-555555555404',
    u.id,
    'EMP-001',
    CURRENT_DATE - INTERVAL '180 days',
    'Software Engineer',
    'ACTIVE',
    'PERMANENT',
    (SELECT id FROM departments WHERE code = 'ENG'),
    (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'),
    NULL,
    (SELECT id FROM work_schedules WHERE name = 'Standard 40h')
FROM users u
WHERE u.email = 'developer@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1
      FROM employees existing
      WHERE existing.user_id = u.id
  );

UPDATE departments
SET head_employee_id = CASE
    WHEN code = 'ENG' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN code = 'OPS' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    ELSE head_employee_id
END
WHERE code IN ('ENG', 'OPS');

INSERT INTO teams (id, code, department_id, name, supervisor_employee_id, is_active)
VALUES
    ('66666666-6666-6666-6666-666666666401', 'ENG_PLATFORM', (SELECT id FROM departments WHERE code = 'ENG'), 'Platform Team', (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), TRUE)
ON CONFLICT (code) DO UPDATE
SET department_id = EXCLUDED.department_id,
    name = EXCLUDED.name,
    supervisor_employee_id = EXCLUDED.supervisor_employee_id,
    is_active = EXCLUDED.is_active;

INSERT INTO projects (id, name, code, status, start_date, end_date)
VALUES
    ('77777777-7777-7777-7777-777777777401', 'HRIS Modernization', 'HRIS-CORE', 'ACTIVE', CURRENT_DATE - INTERVAL '120 days', NULL)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    status = EXCLUDED.status,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date;

INSERT INTO team_project_links (team_id, project_id, start_date, end_date, is_active)
VALUES ((SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM projects WHERE code = 'HRIS-CORE'), CURRENT_DATE - INTERVAL '120 days', NULL, TRUE)
ON CONFLICT (team_id, project_id) DO NOTHING;

INSERT INTO project_departments (project_id, department_id, is_lead)
VALUES ((SELECT id FROM projects WHERE code = 'HRIS-CORE'), (SELECT id FROM departments WHERE code = 'ENG'), TRUE)
ON CONFLICT (project_id, department_id) DO NOTHING;

INSERT INTO project_assignments (id, employee_id, project_id, team_id, supervisor_id, assignment_role, start_date, end_date, is_active)
VALUES
    ('88888888-8888-8888-8888-888888888401', (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), (SELECT id FROM projects WHERE code = 'HRIS-CORE'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), 'MANAGER', CURRENT_DATE - INTERVAL '120 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888402', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM projects WHERE code = 'HRIS-CORE'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), 'MEMBER', CURRENT_DATE - INTERVAL '90 days', NULL, TRUE)
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    project_id = EXCLUDED.project_id,
    team_id = EXCLUDED.team_id,
    supervisor_id = EXCLUDED.supervisor_id,
    assignment_role = EXCLUDED.assignment_role,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    is_active = EXCLUDED.is_active;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('99999999-9999-9999-9999-999999999401', (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 4, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999402', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 2, 3, 0, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO NOTHING;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0401', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), CURRENT_DATE + 7, CURRENT_DATE + 9, 3, 'NORMAL', 'IN_APPROVAL', 'Demo seeded request', NOW() - INTERVAL '1 day', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_workflows (id, subject_type, subject_id, status, created_at, completed_at, version)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0401', 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0401', 'IN_PROGRESS', NOW() - INTERVAL '1 day', NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, workflow_id, approver_id, step_order, status, context, source_type, approver_level, routing_snapshot, comment, decided_at, version)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccc0401', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0401', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'PENDING', 'TEAM', 'TEAM_CHAIN', 1, '{"seed":"demo"}', NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_requests (
    id,
    request_number,
    requester_employee_id,
    requester_user_id,
    type_id,
    subject,
    description,
    status,
    submitted_at,
    due_at,
    created_at,
    updated_at,
    processed_by_user_id
)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddd0401',
    'AR-DEMO-0001',
    (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
    (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
    (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'),
    'Certificate request',
    'Demo administrative request',
    'SUBMITTED',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days',
    NULL
)
ON CONFLICT (request_number) DO NOTHING;

INSERT INTO notifications (id, user_id, title, body, link_path, is_read, created_at)
VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0401', (SELECT id FROM users WHERE email = 'developer@demo.hris.local'), 'Welcome', 'Your demo account is ready.', '/dashboard', FALSE, NOW() - INTERVAL '3 hours')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), id, TIMESTAMPTZ '2026-01-01 09:00:00+00', NULL, TRUE
FROM access_profiles
WHERE code = 'ADMIN_CONSOLE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = (SELECT id FROM users WHERE email = 'admin@demo.hris.local')
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), id, TIMESTAMPTZ '2026-01-01 09:05:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), TRUE
FROM access_profiles
WHERE code = 'HR_CONSOLE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local')
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), id, TIMESTAMPTZ '2026-01-01 09:10:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), TRUE
FROM access_profiles
WHERE code = 'MANAGER_INBOX'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local')
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT (SELECT id FROM users WHERE email = 'developer@demo.hris.local'), id, TIMESTAMPTZ '2026-01-01 09:15:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), TRUE
FROM access_profiles
WHERE code = 'SELF_SERVICE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = (SELECT id FROM users WHERE email = 'developer@demo.hris.local')
        AND existing.profile_id = access_profiles.id
  );

COMMIT;
