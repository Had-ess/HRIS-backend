-- Demo data for disposable local development databases.
-- This keeps the seeded Keycloak users aligned with local HRIS entities
-- so login-required frontend flows have employee and profile data available.

BEGIN;

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333301', 'KC_DEMO_ADMIN', 'admin@demo.hris.local', 'Nadia', 'Ben Salem', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333302', 'KC_DEMO_HR', 'hr.admin@demo.hris.local', 'Sami', 'Khadhraoui', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333303', 'KC_DEMO_MANAGER', 'manager.engineering@demo.hris.local', 'Karim', 'Jlassi', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333304', 'KC_DEMO_EMPLOYEE', 'employee@demo.hris.local', 'Leila', 'Mansour', 'en', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO departments (id, name, code, head_employee_id, is_active)
VALUES
    ('44444444-4444-4444-4444-444444444401', 'Engineering', 'ENG', NULL, TRUE),
    ('44444444-4444-4444-4444-444444444402', 'Operations', 'OPS', NULL, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
VALUES
    ('55555555-5555-5555-5555-555555555401', '33333333-3333-3333-3333-333333333301', 'EMP-ADMIN', CURRENT_DATE - INTERVAL '720 days', 'System Administrator', 'ACTIVE', 'PERMANENT', '44444444-4444-4444-4444-444444444401', NULL, NULL, '11111111-1111-1111-1111-111111111101'),
    ('55555555-5555-5555-5555-555555555402', '33333333-3333-3333-3333-333333333302', 'EMP-HR', CURRENT_DATE - INTERVAL '540 days', 'HR Operations Lead', 'ACTIVE', 'PERMANENT', '44444444-4444-4444-4444-444444444402', NULL, NULL, '11111111-1111-1111-1111-111111111101'),
    ('55555555-5555-5555-5555-555555555403', '33333333-3333-3333-3333-333333333303', 'EMP-MGR', CURRENT_DATE - INTERVAL '365 days', 'Engineering Manager', 'ACTIVE', 'PERMANENT', '44444444-4444-4444-4444-444444444401', NULL, NULL, '11111111-1111-1111-1111-111111111101'),
    ('55555555-5555-5555-5555-555555555404', '33333333-3333-3333-3333-333333333304', 'EMP-001', CURRENT_DATE - INTERVAL '180 days', 'Software Engineer', 'ACTIVE', 'PERMANENT', '44444444-4444-4444-4444-444444444401', '55555555-5555-5555-5555-555555555403', NULL, '11111111-1111-1111-1111-111111111101')
ON CONFLICT (employee_code) DO NOTHING;

UPDATE departments
SET head_employee_id = CASE
    WHEN code = 'ENG' THEN '55555555-5555-5555-5555-555555555403'::uuid
    WHEN code = 'OPS' THEN '55555555-5555-5555-5555-555555555402'::uuid
    ELSE head_employee_id
END
WHERE code IN ('ENG', 'OPS');

INSERT INTO teams (id, code, department_id, name, supervisor_employee_id, is_active)
VALUES
    ('66666666-6666-6666-6666-666666666401', 'ENG_PLATFORM', '44444444-4444-4444-4444-444444444401', 'Platform Team', '55555555-5555-5555-5555-555555555403', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO projects (id, name, code, status, start_date, end_date, project_manager_employee_id)
VALUES
    ('77777777-7777-7777-7777-777777777401', 'HRIS Modernization', 'HRIS-CORE', 'ACTIVE', CURRENT_DATE - INTERVAL '120 days', NULL, '55555555-5555-5555-5555-555555555403')
ON CONFLICT (code) DO NOTHING;

INSERT INTO team_project_links (team_id, project_id, start_date, end_date, is_active)
VALUES ('66666666-6666-6666-6666-666666666401', '77777777-7777-7777-7777-777777777401', CURRENT_DATE - INTERVAL '120 days', NULL, TRUE)
ON CONFLICT (team_id, project_id) DO NOTHING;

INSERT INTO project_departments (project_id, department_id, is_lead)
VALUES ('77777777-7777-7777-7777-777777777401', '44444444-4444-4444-4444-444444444401', TRUE)
ON CONFLICT (project_id, department_id) DO NOTHING;

INSERT INTO project_assignments (id, employee_id, project_id, team_id, supervisor_id, assignment_role, start_date, end_date, is_active)
VALUES
    ('88888888-8888-8888-8888-888888888401', '55555555-5555-5555-5555-555555555403', '77777777-7777-7777-7777-777777777401', '66666666-6666-6666-6666-666666666401', '55555555-5555-5555-5555-555555555403', 'MANAGER', CURRENT_DATE - INTERVAL '120 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888402', '55555555-5555-5555-5555-555555555404', '77777777-7777-7777-7777-777777777401', '66666666-6666-6666-6666-666666666401', '55555555-5555-5555-5555-555555555403', 'MEMBER', CURRENT_DATE - INTERVAL '90 days', NULL, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('99999999-9999-9999-9999-999999999401', '55555555-5555-5555-5555-555555555403', '22222222-2222-2222-2222-222222222201', EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 4, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999402', '55555555-5555-5555-5555-555555555404', '22222222-2222-2222-2222-222222222201', EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 2, 3, 0, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO NOTHING;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0401', '55555555-5555-5555-5555-555555555404', '22222222-2222-2222-2222-222222222201', CURRENT_DATE + 7, CURRENT_DATE + 9, 3, 'NORMAL', 'IN_APPROVAL', 'Demo seeded request', NOW() - INTERVAL '1 day', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_workflows (id, subject_type, subject_id, status, created_at, completed_at, version)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0401', 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0401', 'IN_PROGRESS', NOW() - INTERVAL '1 day', NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, workflow_id, approver_id, step_order, status, context, source_type, approver_level, routing_snapshot, comment, decided_at, version)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccc0401', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0401', '33333333-3333-3333-3333-333333333303', 1, 'PENDING', 'LEAVE_REQUEST', 'PROJECT_CHAIN', 1, '{"seed":"demo"}', NULL, NULL, 0)
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
    '55555555-5555-5555-5555-555555555404',
    '33333333-3333-3333-3333-333333333304',
    '33333333-3333-3333-3333-333333333201',
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
VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0401', '33333333-3333-3333-3333-333333333304', 'Welcome', 'Your demo account is ready.', '/dashboard', FALSE, NOW() - INTERVAL '3 hours')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT '33333333-3333-3333-3333-333333333301', id, TIMESTAMPTZ '2026-01-01 09:00:00+00', NULL, TRUE
FROM access_profiles
WHERE code = 'ADMIN_CONSOLE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = '33333333-3333-3333-3333-333333333301'
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT '33333333-3333-3333-3333-333333333302', id, TIMESTAMPTZ '2026-01-01 09:05:00+00', '33333333-3333-3333-3333-333333333301', TRUE
FROM access_profiles
WHERE code = 'HR_CONSOLE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = '33333333-3333-3333-3333-333333333302'
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT '33333333-3333-3333-3333-333333333303', id, TIMESTAMPTZ '2026-01-01 09:10:00+00', '33333333-3333-3333-3333-333333333301', TRUE
FROM access_profiles
WHERE code = 'MANAGER_INBOX'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = '33333333-3333-3333-3333-333333333303'
        AND existing.profile_id = access_profiles.id
  );

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT '33333333-3333-3333-3333-333333333304', id, TIMESTAMPTZ '2026-01-01 09:15:00+00', '33333333-3333-3333-3333-333333333301', TRUE
FROM access_profiles
WHERE code = 'SELF_SERVICE'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = '33333333-3333-3333-3333-333333333304'
        AND existing.profile_id = access_profiles.id
  );

COMMIT;
