-- Comprehensive local demo seed for existing HRIS modules only.
-- Expected Keycloak test emails:
--   admin@demo.hris.local
--   hr.admin@demo.hris.local
--   manager.engineering@demo.hris.local
--   supervisor.operations@demo.hris.local
--   developer@demo.hris.local
--   product@demo.hris.local
--   office@demo.hris.local
--   analyst@demo.hris.local
--   director@demo.hris.local
--   qa@demo.hris.local
--   legal@demo.hris.local
--   finance.viewer@demo.hris.local

BEGIN;

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_seed, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333309', 'KC_DEMO_DIRECTOR', 'director@demo.hris.local', 'Fawzi', 'Drissi', 'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333310', 'KC_DEMO_QA', 'qa@demo.hris.local', 'Ines', 'Karoui', 'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333311', 'KC_DEMO_LEGAL', 'legal@demo.hris.local', 'Hela', 'Nasri', 'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333312', 'KC_DEMO_FINANCE', 'finance.viewer@demo.hris.local', 'Mehdi', 'Saadi', 'en', TRUE, TRUE)
ON CONFLICT (email) DO UPDATE
SET keycloak_id = EXCLUDED.keycloak_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    locale_preference = EXCLUDED.locale_preference,
    is_seed = EXCLUDED.is_seed,
    is_active = EXCLUDED.is_active;

INSERT INTO departments (id, name, code, head_employee_id, is_active, openings)
VALUES
    ('44444444-4444-4444-4444-444444444406', 'Product', 'PROD', NULL, TRUE, 1),
    ('44444444-4444-4444-4444-444444444407', 'Marketing', 'MKT', NULL, TRUE, 2),
    ('44444444-4444-4444-4444-444444444408', 'Legal', 'LEGAL', NULL, TRUE, 0),
    ('44444444-4444-4444-4444-444444444409', 'Data', 'DATA', NULL, TRUE, 1),
    ('44444444-4444-4444-4444-444444444410', 'Sales', 'SALES', NULL, TRUE, 3)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    openings = EXCLUDED.openings;

UPDATE users
SET is_seed = TRUE
WHERE email IN (
    'admin@demo.hris.local',
    'hr.admin@demo.hris.local',
    'manager.engineering@demo.hris.local',
    'supervisor.operations@demo.hris.local',
    'developer@demo.hris.local',
    'product@demo.hris.local',
    'office@demo.hris.local',
    'analyst@demo.hris.local',
    'director@demo.hris.local',
    'qa@demo.hris.local',
    'legal@demo.hris.local',
    'finance.viewer@demo.hris.local'
);

INSERT INTO employees (
    id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id,
    supervisor_employee_id, termination_date, work_schedule_id, location, cin
)
VALUES
    ('55555555-5555-5555-5555-555555555408', (SELECT id FROM users WHERE email = 'director@demo.hris.local'), 'EMP-DIR', CURRENT_DATE - INTERVAL '900 days', 'Director', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'OPS'), NULL, NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h'), 'Tunis HQ', '12000001'),
    ('55555555-5555-5555-5555-555555555409', (SELECT id FROM users WHERE email = 'qa@demo.hris.local'), 'EMP-QA', CURRENT_DATE - INTERVAL '240 days', 'QA Engineer', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h'), 'Tunis HQ', '12000002'),
    ('55555555-5555-5555-5555-555555555410', (SELECT id FROM users WHERE email = 'legal@demo.hris.local'), 'EMP-LEGAL', CURRENT_DATE - INTERVAL '420 days', 'Legal Counsel', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'LEGAL'), (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h'), 'Tunis HQ', '12000003'),
    ('55555555-5555-5555-5555-555555555411', (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), 'EMP-FINV', CURRENT_DATE - INTERVAL '300 days', 'Finance Analyst', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'FIN'), (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h'), 'Sfax Office', '12000004')
ON CONFLICT (employee_code) DO UPDATE
SET user_id = EXCLUDED.user_id,
    hire_date = EXCLUDED.hire_date,
    job_title = EXCLUDED.job_title,
    status = EXCLUDED.status,
    contract_type = EXCLUDED.contract_type,
    department_id = EXCLUDED.department_id,
    supervisor_employee_id = EXCLUDED.supervisor_employee_id,
    termination_date = EXCLUDED.termination_date,
    work_schedule_id = EXCLUDED.work_schedule_id,
    location = EXCLUDED.location,
    cin = EXCLUDED.cin;

UPDATE employees
SET supervisor_employee_id = (SELECT id FROM employees WHERE employee_code = 'EMP-DIR')
WHERE employee_code IN ('EMP-HR', 'EMP-MGR');

UPDATE departments
SET head_employee_id = CASE code
    WHEN 'ENG' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN 'OPS' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-DIR')
    WHEN 'HR' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    WHEN 'IT' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN 'FIN' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-FINV')
    WHEN 'PROD' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-002')
    WHEN 'MKT' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-003')
    WHEN 'LEGAL' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL')
    WHEN 'DATA' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-004')
    WHEN 'SALES' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    ELSE head_employee_id
END
WHERE code IN ('ENG', 'OPS', 'HR', 'IT', 'FIN', 'PROD', 'MKT', 'LEGAL', 'DATA', 'SALES');

INSERT INTO teams (id, code, department_id, name, supervisor_employee_id, is_active)
VALUES
    ('66666666-6666-6666-6666-666666666405', 'PROD_CORE', (SELECT id FROM departments WHERE code = 'PROD'), 'Product Core', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), TRUE),
    ('66666666-6666-6666-6666-666666666406', 'DATA_LAKE', (SELECT id FROM departments WHERE code = 'DATA'), 'Data Lake', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), TRUE),
    ('66666666-6666-6666-6666-666666666407', 'LEGAL_COMPLIANCE', (SELECT id FROM departments WHERE code = 'LEGAL'), 'Legal Compliance', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), TRUE),
    ('66666666-6666-6666-6666-666666666408', 'OPS_SUPPORT', (SELECT id FROM departments WHERE code = 'OPS'), 'Operations Support', (SELECT id FROM employees WHERE employee_code = 'EMP-003'), TRUE)
ON CONFLICT (code) DO UPDATE
SET department_id = EXCLUDED.department_id,
    name = EXCLUDED.name,
    supervisor_employee_id = EXCLUDED.supervisor_employee_id,
    is_active = EXCLUDED.is_active;

INSERT INTO projects (id, name, code, status, start_date, end_date)
VALUES
    ('77777777-7777-7777-7777-777777777404', 'Atlas Platform Migration', 'P-ATLAS', 'ACTIVE', CURRENT_DATE - INTERVAL '120 days', CURRENT_DATE + INTERVAL '120 days'),
    ('77777777-7777-7777-7777-777777777405', 'Mobile App v3', 'P-MOBILE3', 'ACTIVE', CURRENT_DATE - INTERVAL '80 days', CURRENT_DATE + INTERVAL '210 days'),
    ('77777777-7777-7777-7777-777777777406', 'Brand Refresh 2026', 'P-BRAND26', 'ACTIVE', CURRENT_DATE - INTERVAL '95 days', CURRENT_DATE + INTERVAL '45 days'),
    ('77777777-7777-7777-7777-777777777407', 'Data Lake Foundations', 'P-DATALAKE', 'ON_HOLD', CURRENT_DATE - INTERVAL '60 days', CURRENT_DATE + INTERVAL '90 days'),
    ('77777777-7777-7777-7777-777777777408', 'ISO 27001 Re-certification', 'P-ISO27001', 'PLANNED', CURRENT_DATE + INTERVAL '15 days', CURRENT_DATE + INTERVAL '180 days')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    status = EXCLUDED.status,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date;

INSERT INTO team_project_links (team_id, project_id, start_date, end_date, is_active)
VALUES
    ((SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM projects WHERE code = 'P-ATLAS'), CURRENT_DATE - INTERVAL '120 days', NULL, TRUE),
    ((SELECT id FROM teams WHERE code = 'PROD_CORE'), (SELECT id FROM projects WHERE code = 'P-MOBILE3'), CURRENT_DATE - INTERVAL '70 days', NULL, TRUE),
    ((SELECT id FROM teams WHERE code = 'DATA_LAKE'), (SELECT id FROM projects WHERE code = 'P-DATALAKE'), CURRENT_DATE - INTERVAL '60 days', NULL, TRUE),
    ((SELECT id FROM teams WHERE code = 'LEGAL_COMPLIANCE'), (SELECT id FROM projects WHERE code = 'P-ISO27001'), CURRENT_DATE + INTERVAL '15 days', NULL, TRUE)
ON CONFLICT (team_id, project_id) DO NOTHING;

INSERT INTO project_departments (project_id, department_id, is_lead)
VALUES
    ((SELECT id FROM projects WHERE code = 'P-ATLAS'), (SELECT id FROM departments WHERE code = 'ENG'), TRUE),
    ((SELECT id FROM projects WHERE code = 'P-MOBILE3'), (SELECT id FROM departments WHERE code = 'PROD'), TRUE),
    ((SELECT id FROM projects WHERE code = 'P-BRAND26'), (SELECT id FROM departments WHERE code = 'MKT'), TRUE),
    ((SELECT id FROM projects WHERE code = 'P-DATALAKE'), (SELECT id FROM departments WHERE code = 'DATA'), TRUE),
    ((SELECT id FROM projects WHERE code = 'P-ISO27001'), (SELECT id FROM departments WHERE code = 'LEGAL'), TRUE)
ON CONFLICT (project_id, department_id) DO NOTHING;

INSERT INTO project_assignments (id, employee_id, project_id, team_id, supervisor_id, assignment_role, start_date, end_date, is_active)
VALUES
    ('88888888-8888-8888-8888-888888888403', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM projects WHERE code = 'P-ATLAS'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), 'MEMBER', CURRENT_DATE - INTERVAL '110 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888404', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM projects WHERE code = 'P-ATLAS'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), 'MEMBER', CURRENT_DATE - INTERVAL '100 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888405', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM projects WHERE code = 'P-MOBILE3'), (SELECT id FROM teams WHERE code = 'PROD_CORE'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), 'LEAD', CURRENT_DATE - INTERVAL '70 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888406', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), (SELECT id FROM projects WHERE code = 'P-DATALAKE'), (SELECT id FROM teams WHERE code = 'DATA_LAKE'), (SELECT id FROM employees WHERE employee_code = 'EMP-DIR'), 'LEAD', CURRENT_DATE - INTERVAL '60 days', NULL, TRUE),
    ('88888888-8888-8888-8888-888888888407', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM projects WHERE code = 'P-ISO27001'), (SELECT id FROM teams WHERE code = 'LEGAL_COMPLIANCE'), (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), 'LEAD', CURRENT_DATE + INTERVAL '15 days', NULL, TRUE)
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    project_id = EXCLUDED.project_id,
    team_id = EXCLUDED.team_id,
    supervisor_id = EXCLUDED.supervisor_id,
    assignment_role = EXCLUDED.assignment_role,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    is_active = EXCLUDED.is_active;

INSERT INTO hr_holidays (id, calendar_id, date, name, is_recurring, created_at, updated_at)
VALUES
    ('18181818-1818-1818-1818-181818181801', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 3, 20), 'Independence Day', TRUE, NOW(), NOW()),
    ('18181818-1818-1818-1818-181818181802', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 7, 25), 'Republic Day', TRUE, NOW(), NOW()),
    ('18181818-1818-1818-1818-181818181803', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 8, 13), 'Women''s Day', TRUE, NOW(), NOW())
ON CONFLICT (calendar_id, date, name) DO NOTHING;

INSERT INTO team_hierarchy_relations (id, team_id, responsible_employee_id, collaborator_employee_id, status, start_date, end_date)
SELECT *
FROM (
    VALUES
        ('12121212-1212-1212-1212-121212121204'::uuid, '66666666-6666-6666-6666-666666666405'::uuid, NULL::uuid, (SELECT id FROM employees WHERE employee_code = 'EMP-002'), 'ACTIVE', CURRENT_DATE - INTERVAL '120 days', NULL::date),
        ('12121212-1212-1212-1212-121212121205'::uuid, '66666666-6666-6666-6666-666666666405'::uuid, (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM employees WHERE employee_code = 'EMP-003'), 'ACTIVE', CURRENT_DATE - INTERVAL '90 days', NULL::date),
        ('12121212-1212-1212-1212-121212121206'::uuid, '66666666-6666-6666-6666-666666666406'::uuid, NULL::uuid, (SELECT id FROM employees WHERE employee_code = 'EMP-004'), 'ACTIVE', CURRENT_DATE - INTERVAL '90 days', NULL::date),
        ('12121212-1212-1212-1212-121212121207'::uuid, '66666666-6666-6666-6666-666666666401'::uuid, (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), 'ACTIVE', CURRENT_DATE - INTERVAL '80 days', NULL::date)
) AS seed(id, team_id, responsible_employee_id, collaborator_employee_id, status, start_date, end_date)
WHERE NOT EXISTS (
    SELECT 1 FROM team_hierarchy_relations existing WHERE existing.id = seed.id
);

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('99999999-9999-9999-9999-999999999407', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 6, 4, 1, 0),
    ('99999999-9999-9999-9999-999999999408', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM leave_types WHERE code = 'SICK'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 15, 2, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999409', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 4, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999410', (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 3, 0, 2, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO NOTHING;

INSERT INTO leave_balance_transactions (id, employee_id, leave_type_id, transaction_type, amount, balance_after, source_type, source_id, comment, created_by_user_id, occurred_at)
VALUES
    ('15151515-1515-1515-1515-151515151505', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'ACCRUAL', 24, 24, 'ACQUISITION_POLICY', (SELECT id FROM leave_acquisition_policies WHERE code = 'ANNUAL_MONTHLY'), 'Initial seeded annual accrual', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '120 days'),
    ('15151515-1515-1515-1515-151515151506', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'REQUEST_RESERVATION', -4, 18, 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0406', 'Reserved for upcoming leave', (SELECT id FROM users WHERE email = 'qa@demo.hris.local'), NOW() - INTERVAL '2 days'),
    ('15151515-1515-1515-1515-151515151507', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'MANUAL_ADJUSTMENT', 1, 21, 'MANUAL_ADJUSTMENT', NULL, 'HR manual correction', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '12 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version, is_half_day)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0406', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), CURRENT_DATE + 6, CURRENT_DATE + 10, 5, 'NORMAL', 'PENDING', 'Family trip to Hammamet', NOW() - INTERVAL '3 days', 0, FALSE),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0407', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'PATERNITY'), CURRENT_DATE + 14, CURRENT_DATE + 20, 7, 'URGENT', 'IN_APPROVAL', 'Birth of child', NOW() - INTERVAL '2 days', 0, FALSE),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0408', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), CURRENT_DATE + 2, CURRENT_DATE + 2, 1, 'NORMAL', 'APPROVED', 'Administrative appointment', NOW() - INTERVAL '7 days', 0, TRUE),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0409', (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'), (SELECT id FROM leave_types WHERE code = 'SICK'), CURRENT_DATE - 4, CURRENT_DATE - 3, 2, 'URGENT', 'APPROVED', 'Medical leave', NOW() - INTERVAL '5 days', 0, FALSE),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0410', (SELECT id FROM employees WHERE employee_code = 'EMP-003'), (SELECT id FROM leave_types WHERE code = 'UNPAID'), CURRENT_DATE + 25, CURRENT_DATE + 35, 8, 'NORMAL', 'REJECTED', 'Extended personal leave', NOW() - INTERVAL '6 days', 0, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_workflows (id, subject_type, subject_id, status, created_at, completed_at, version)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0402', 'LEAVE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0406', 'IN_PROGRESS', NOW() - INTERVAL '3 days', NULL, 0),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0403', 'LEAVE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0407', 'IN_PROGRESS', NOW() - INTERVAL '2 days', NULL, 0),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0404', 'LEAVE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0408', 'COMPLETED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days', 0),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0405', 'LEAVE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0409', 'COMPLETED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, workflow_id, approver_id, step_order, status, context, source_type, approver_level, routing_snapshot, comment, decided_at, version)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccc0402', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0402', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'PENDING', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', NULL, NULL, 0),
    ('cccccccc-cccc-cccc-cccc-cccccccc0403', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0403', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Coverage confirmed', NOW() - INTERVAL '1 day', 0),
    ('cccccccc-cccc-cccc-cccc-cccccccc0404', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0403', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 2, 'PENDING', 'TEAM', 'TEAM_CHAIN', 2, '{"route":"hr_validation"}', NULL, NULL, 0),
    ('cccccccc-cccc-cccc-cccc-cccccccc0405', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0404', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Approved', NOW() - INTERVAL '6 days', 0),
    ('cccccccc-cccc-cccc-cccc-cccccccc0406', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0405', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Medical proof received', NOW() - INTERVAL '4 days', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO file_attachments (id, request_id, file_name, mime_type, storage_path, uploaded_at, uploaded_by_id)
VALUES
    ('f1f1f1f1-f1f1-f1f1-f1f1-f1f1f1f1f101', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0409', 'medical-note.pdf', 'application/pdf', '/demo/leave/medical-note.pdf', NOW() - INTERVAL '5 days', (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'))
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_requests (
    id, request_number, requester_employee_id, requester_user_id, type_id, subject, description, status,
    submitted_at, due_at, created_at, updated_at, processed_by_user_id, reviewed_at, decided_at, completed_at, rejection_reason
)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddd0406', 'AR-DEMO-0006', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM users WHERE email = 'qa@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'EQUIPMENT'), 'QA laptop replacement', 'Current test laptop battery is failing.', 'IN_REVIEW', NOW() - INTERVAL '4 days', NOW() + INTERVAL '1 day', NOW() - INTERVAL '4 days', NOW() - INTERVAL '1 day', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '3 days', NULL, NULL, NULL),
    ('dddddddd-dddd-dddd-dddd-dddddddd0407', 'AR-DEMO-0007', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM users WHERE email = 'legal@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'), 'Employment certificate', 'Required for visa support file.', 'SUBMITTED', NOW() - INTERVAL '2 days', NOW() + INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NULL, NULL, NULL, NULL, NULL),
    ('dddddddd-dddd-dddd-dddd-dddddddd0408', 'AR-DEMO-0008', (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'), (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'INFO_UPDATE'), 'Bank details update', 'Need to update salary transfer account details.', 'COMPLETED', NOW() - INTERVAL '9 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '9 days', NOW() - INTERVAL '4 days', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '8 days', NOW() - INTERVAL '6 days', NOW() - INTERVAL '4 days', NULL)
ON CONFLICT (request_number) DO NOTHING;

INSERT INTO admin_request_comments (id, admin_request_id, author_user_id, comment, internal, created_at)
VALUES
    ('adadadad-adad-adad-adad-adadadad0101', 'dddddddd-dddd-dddd-dddd-dddddddd0406', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'Procurement quote requested from IT.', TRUE, NOW() - INTERVAL '2 days'),
    ('adadadad-adad-adad-adad-adadadad0102', 'dddddddd-dddd-dddd-dddd-dddddddd0406', (SELECT id FROM users WHERE email = 'qa@demo.hris.local'), 'Battery now lasts less than one hour.', FALSE, NOW() - INTERVAL '36 hours'),
    ('adadadad-adad-adad-adad-adadadad0103', 'dddddddd-dddd-dddd-dddd-dddddddd0408', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'Updated in payroll and bank export mapping.', TRUE, NOW() - INTERVAL '5 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_request_attachments (id, admin_request_id, file_name, content_type, size_bytes, storage_path, response_document, uploaded_by_user_id, uploaded_at)
VALUES
    ('bdbdbdbd-bdbd-bdbd-bdbd-bdbdbdbd0101', 'dddddddd-dddd-dddd-dddd-dddddddd0406', 'quote.pdf', 'application/pdf', 142000, '/demo/admin/quote.pdf', FALSE, (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '2 days'),
    ('bdbdbdbd-bdbd-bdbd-bdbd-bdbdbdbd0102', 'dddddddd-dddd-dddd-dddd-dddddddd0408', 'bank-update-confirmation.pdf', 'application/pdf', 98000, '/demo/admin/bank-update-confirmation.pdf', TRUE, (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '4 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (id, user_id, title, body, link_path, is_read, created_at, type, actor_display_name)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0407', (SELECT id FROM users WHERE email = 'director@demo.hris.local'), 'Executive dashboard updated', 'Today''s headcount and leave metrics are available.', '/dashboard', FALSE, NOW() - INTERVAL '90 minutes', 'SYSTEM', NULL),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0408', (SELECT id FROM users WHERE email = 'qa@demo.hris.local'), 'Leave request pending', 'Your annual leave request is waiting for manager validation.', '/leave', FALSE, NOW() - INTERVAL '3 hours', 'LEAVE', 'Karim Jlassi'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0409', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'New admin request submitted', 'A work certificate request requires handling.', '/admin/admin-requests', FALSE, NOW() - INTERVAL '2 hours', 'REQUEST', 'Hela Nasri'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0410', (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), 'Admin request completed', 'Your bank details update has been completed.', '/admin-requests', TRUE, NOW() - INTERVAL '4 days', 'REQUEST', 'Sami Khadhraoui')
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (id, actor_id, action, resource, resource_id, previous_state, new_state, ip_address, timestamp, actor_type, risk_level)
VALUES
    ('17171717-1717-1717-1717-171717171706', (SELECT id FROM users WHERE email = 'director@demo.hris.local'), 'READ', 'analytics_dashboard', NULL, NULL, '{"scope":"GLOBAL"}', '127.0.0.1', NOW() - INTERVAL '6 hours', 'USER', 'LOW'),
    ('17171717-1717-1717-1717-171717171707', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'UPDATE', 'admin_request', 'dddddddd-dddd-dddd-dddd-dddddddd0406', '{"status":"SUBMITTED"}', '{"status":"IN_REVIEW"}', '127.0.0.1', NOW() - INTERVAL '3 days', 'USER', 'MEDIUM'),
    ('17171717-1717-1717-1717-171717171708', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'APPROVE', 'approval_step', 'cccccccc-cccc-cccc-cccc-cccccccc0403', '{"status":"PENDING"}', '{"status":"APPROVED"}', '127.0.0.1', NOW() - INTERVAL '1 day', 'USER', 'LOW'),
    ('17171717-1717-1717-1717-171717171709', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'UPDATE', 'leave_balance', '99999999-9999-9999-9999-999999999409', '{"availableDays":20}', '{"availableDays":21}', '127.0.0.1', NOW() - INTERVAL '12 days', 'USER', 'MEDIUM')
ON CONFLICT (id) DO NOTHING;

INSERT INTO analytics_events (id, event_type, aggregate_type, aggregate_id, occurred_at, event_date, payload, processed_at, retry_count, last_error)
VALUES
    ('19191919-1919-1919-1919-191919191901', 'LEAVE_REQUEST_SUBMITTED', 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0406', NOW() - INTERVAL '3 days', CURRENT_DATE - 3, '{"status":"PENDING","employeeCode":"EMP-QA"}', NOW() - INTERVAL '3 days', 0, NULL),
    ('19191919-1919-1919-1919-191919191902', 'ADMIN_REQUEST_SUBMITTED', 'ADMIN_REQUEST', 'dddddddd-dddd-dddd-dddd-dddddddd0407', NOW() - INTERVAL '2 days', CURRENT_DATE - 2, '{"status":"SUBMITTED","employeeCode":"EMP-LEGAL"}', NOW() - INTERVAL '2 days', 0, NULL),
    ('19191919-1919-1919-1919-191919191903', 'APPROVAL_STEP_APPROVED', 'APPROVAL_STEP', 'cccccccc-cccc-cccc-cccc-cccccccc0403', NOW() - INTERVAL '1 day', CURRENT_DATE - 1, '{"status":"APPROVED","workflow":"LEAVE"}', NOW() - INTERVAL '1 day', 0, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO analytics_leave_facts (id, event_date, leave_request_id, employee_id, department_id, project_id, team_id, leave_type_id, working_days, request_status, approval_duration_days)
VALUES
    ('20202020-2020-2020-2020-202020202001', CURRENT_DATE - 3, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0406', (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM projects WHERE code = 'P-ATLAS'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 5, 'PENDING', 0),
    ('20202020-2020-2020-2020-202020202002', CURRENT_DATE - 2, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0407', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM departments WHERE code = 'PROD'), (SELECT id FROM projects WHERE code = 'P-MOBILE3'), (SELECT id FROM teams WHERE code = 'PROD_CORE'), (SELECT id FROM leave_types WHERE code = 'PATERNITY'), 7, 'IN_APPROVAL', 1),
    ('20202020-2020-2020-2020-202020202003', CURRENT_DATE - 7, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0408', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM departments WHERE code = 'LEGAL'), (SELECT id FROM projects WHERE code = 'P-ISO27001'), (SELECT id FROM teams WHERE code = 'LEGAL_COMPLIANCE'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), 1, 'APPROVED', 1),
    ('20202020-2020-2020-2020-202020202004', CURRENT_DATE - 5, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0409', (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'), (SELECT id FROM departments WHERE code = 'FIN'), NULL, NULL, (SELECT id FROM leave_types WHERE code = 'SICK'), 2, 'APPROVED', 1)
ON CONFLICT (leave_request_id) DO NOTHING;

INSERT INTO analytics_approval_facts (id, event_date, workflow_id, approval_step_id, approver_id, subject_type, source_type, approver_level, step_status, decision_delay_days)
VALUES
    ('21212121-2121-2121-2121-212121212101', CURRENT_DATE - 1, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0403', 'cccccccc-cccc-cccc-cccc-cccccccc0403', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'LEAVE', 'TEAM_CHAIN', 1, 'APPROVED', 1),
    ('21212121-2121-2121-2121-212121212102', CURRENT_DATE - 6, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0404', 'cccccccc-cccc-cccc-cccc-cccccccc0405', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'LEAVE', 'TEAM_CHAIN', 1, 'APPROVED', 1)
ON CONFLICT (approval_step_id) DO NOTHING;

INSERT INTO analytics_headcount_facts (id, snapshot_date, employee_id, department_id, team_id, employee_status, is_active)
VALUES
    ('22222222-aaaa-bbbb-cccc-000000000001', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM departments WHERE code = 'ENG'), NULL, 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000002', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), (SELECT id FROM departments WHERE code = 'OPS'), NULL, 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000003', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000004', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000005', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM departments WHERE code = 'PROD'), (SELECT id FROM teams WHERE code = 'PROD_CORE'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000006', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-003'), (SELECT id FROM departments WHERE code = 'OPS'), (SELECT id FROM teams WHERE code = 'OPS_SUPPORT'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000007', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-004'), (SELECT id FROM departments WHERE code = 'DATA'), (SELECT id FROM teams WHERE code = 'DATA_LAKE'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000008', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-DIR'), (SELECT id FROM departments WHERE code = 'OPS'), NULL, 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000009', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-QA'), (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000010', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM departments WHERE code = 'LEGAL'), (SELECT id FROM teams WHERE code = 'LEGAL_COMPLIANCE'), 'ACTIVE', TRUE),
    ('22222222-aaaa-bbbb-cccc-000000000011', CURRENT_DATE, (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'), (SELECT id FROM departments WHERE code = 'FIN'), NULL, 'ACTIVE', TRUE)
ON CONFLICT (snapshot_date, employee_id) DO NOTHING;

INSERT INTO analytics_project_absence_facts (id, snapshot_date, project_id, team_id, absent_employees, absence_days, affected_members, estimated_delay_days, risk_level)
VALUES
    ('23232323-2323-2323-2323-232323232301', CURRENT_DATE, (SELECT id FROM projects WHERE code = 'P-ATLAS'), (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), 2, 7, 4, 2, 'MEDIUM'),
    ('23232323-2323-2323-2323-232323232302', CURRENT_DATE, (SELECT id FROM projects WHERE code = 'P-DATALAKE'), (SELECT id FROM teams WHERE code = 'DATA_LAKE'), 1, 3, 2, 1, 'LOW'),
    ('23232323-2323-2323-2323-232323232303', CURRENT_DATE, (SELECT id FROM projects WHERE code = 'P-ISO27001'), (SELECT id FROM teams WHERE code = 'LEGAL_COMPLIANCE'), 1, 5, 1, 3, 'HIGH')
ON CONFLICT (id) DO NOTHING;

INSERT INTO analytics_leave_metrics_snapshots (id, snapshot_date, scope_type, scope_id, total_requests, approved_count, rejected_count, pending_count, average_processing_days)
VALUES
    ('24242424-2424-2424-2424-242424242401', CURRENT_DATE, 'GLOBAL', NULL, 10, 5, 1, 4, 2.40),
    ('24242424-2424-2424-2424-242424242402', CURRENT_DATE, 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 2, 0, 2, 1.80),
    ('24242424-2424-2424-2424-242424242403', CURRENT_DATE, 'TEAM', (SELECT id FROM teams WHERE code = 'ENG_PLATFORM'), 3, 1, 0, 2, 1.50)
ON CONFLICT (snapshot_date, scope_type, scope_id) DO NOTHING;

INSERT INTO analytics_headcount_metrics_snapshots (id, snapshot_date, scope_type, scope_id, total_employees, active_employees, new_hires, terminated_employees)
VALUES
    ('25252525-2525-2525-2525-252525252501', CURRENT_DATE, 'GLOBAL', NULL, 11, 11, 2, 0),
    ('25252525-2525-2525-2525-252525252502', CURRENT_DATE, 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 4, 1, 0),
    ('25252525-2525-2525-2525-252525252503', CURRENT_DATE, 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'OPS'), 3, 3, 1, 0)
ON CONFLICT (snapshot_date, scope_type, scope_id) DO NOTHING;

INSERT INTO analytics_leave_distribution_snapshots (id, snapshot_date, scope_type, scope_id, leave_type_id, request_count, total_days)
VALUES
    ('26262626-2626-2626-2626-262626262601', CURRENT_DATE, 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 4, 17),
    ('26262626-2626-2626-2626-262626262602', CURRENT_DATE, 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'SICK'), 2, 4),
    ('26262626-2626-2626-2626-262626262603', CURRENT_DATE, 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'PATERNITY'), 1, 7),
    ('26262626-2626-2626-2626-262626262604', CURRENT_DATE, 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 2, 8)
ON CONFLICT (snapshot_date, scope_type, scope_id, leave_type_id) DO NOTHING;

INSERT INTO analytics_approval_bottleneck_snapshots (id, snapshot_date, scope_type, scope_id, source_type, approver_level, pending_count, average_decision_days, rejection_rate)
VALUES
    ('27272727-2727-2727-2727-272727272701', CURRENT_DATE, 'GLOBAL', NULL, 'TEAM_CHAIN', 1, 3, 1.80, 0.10),
    ('27272727-2727-2727-2727-272727272702', CURRENT_DATE, 'GLOBAL', NULL, 'TEAM_CHAIN', 2, 1, 2.60, 0.00),
    ('27272727-2727-2727-2727-272727272703', CURRENT_DATE, 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 'TEAM_CHAIN', 1, 2, 1.20, 0.00)
ON CONFLICT (snapshot_date, scope_type, scope_id, source_type, approver_level) DO NOTHING;

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT seed.user_id, ap.id, seed.assigned_at, seed.assigned_by_id, TRUE
FROM (
    VALUES
        ((SELECT id FROM users WHERE email = 'director@demo.hris.local'), 'ADMIN_CONSOLE', TIMESTAMPTZ '2026-01-01 09:25:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'qa@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:26:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'legal@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:27:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:28:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), 'HR_CONSOLE', TIMESTAMPTZ '2026-01-02 09:28:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'))
) AS seed(user_id, profile_code, assigned_at, assigned_by_id)
JOIN access_profiles ap ON ap.code = seed.profile_code
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profile_assignments existing
    WHERE existing.user_id = seed.user_id
      AND existing.profile_id = ap.id
      AND existing.assigned_at = seed.assigned_at
);

COMMIT;
