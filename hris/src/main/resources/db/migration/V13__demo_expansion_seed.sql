BEGIN;

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333305', 'KC_DEMO_PRODUCT', 'product@demo.hris.local', 'Rim', 'Ayedi', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333306', 'KC_DEMO_OFFICE', 'office@demo.hris.local', 'Hedi', 'Gharbi', 'fr', TRUE),
    ('33333333-3333-3333-3333-333333333307', 'KC_DEMO_ANALYST', 'analyst@demo.hris.local', 'Walid', 'Mrad', 'en', TRUE)
ON CONFLICT (email) DO UPDATE
SET keycloak_id = EXCLUDED.keycloak_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    locale_preference = EXCLUDED.locale_preference,
    is_active = EXCLUDED.is_active;

INSERT INTO departments (id, name, code, head_employee_id, is_active)
VALUES
    ('44444444-4444-4444-4444-444444444403', 'Human Resources', 'HR', NULL, TRUE),
    ('44444444-4444-4444-4444-444444444404', 'Information Technology', 'IT', NULL, TRUE),
    ('44444444-4444-4444-4444-444444444405', 'Finance', 'FIN', NULL, TRUE)
ON CONFLICT (code) DO NOTHING;

UPDATE employees
SET department_id = '44444444-4444-4444-4444-444444444403'
WHERE id = '55555555-5555-5555-5555-555555555402';

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, supervisor_employee_id, termination_date, work_schedule_id)
VALUES
    ('55555555-5555-5555-5555-555555555405', (SELECT id FROM users WHERE email = 'product@demo.hris.local'), 'EMP-002', CURRENT_DATE - INTERVAL '220 days', 'Product Owner', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'IT'), (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h')),
    ('55555555-5555-5555-5555-555555555406', (SELECT id FROM users WHERE email = 'office@demo.hris.local'), 'EMP-003', CURRENT_DATE - INTERVAL '160 days', 'Operations Coordinator', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'OPS'), (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h')),
    ('55555555-5555-5555-5555-555555555407', (SELECT id FROM users WHERE email = 'analyst@demo.hris.local'), 'EMP-004', CURRENT_DATE - INTERVAL '90 days', 'Financial Analyst', 'ACTIVE', 'PERMANENT', (SELECT id FROM departments WHERE code = 'FIN'), (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), NULL, (SELECT id FROM work_schedules WHERE name = 'Standard 40h'))
ON CONFLICT (employee_code) DO UPDATE
SET user_id = EXCLUDED.user_id,
    hire_date = EXCLUDED.hire_date,
    job_title = EXCLUDED.job_title,
    status = EXCLUDED.status,
    contract_type = EXCLUDED.contract_type,
    department_id = EXCLUDED.department_id,
    supervisor_employee_id = EXCLUDED.supervisor_employee_id,
    termination_date = EXCLUDED.termination_date,
    work_schedule_id = EXCLUDED.work_schedule_id;

UPDATE departments
SET head_employee_id = CASE
    WHEN code = 'ENG' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN code = 'OPS' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    WHEN code = 'HR' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    WHEN code = 'IT' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN code = 'FIN' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-004')
    ELSE head_employee_id
END
WHERE code IN ('ENG', 'OPS', 'HR', 'IT', 'FIN');

INSERT INTO teams (id, code, department_id, name, supervisor_employee_id, is_active)
VALUES
    ('66666666-6666-6666-6666-666666666402', 'HR_TEAM', (SELECT id FROM departments WHERE code = 'HR'), 'HR Team', (SELECT id FROM employees WHERE employee_code = 'EMP-HR'), TRUE),
    ('66666666-6666-6666-6666-666666666403', 'IT_TEAM', (SELECT id FROM departments WHERE code = 'IT'), 'IT Team', (SELECT id FROM employees WHERE employee_code = 'EMP-MGR'), TRUE),
    ('66666666-6666-6666-6666-666666666404', 'FIN_TEAM', (SELECT id FROM departments WHERE code = 'FIN'), 'Finance Team', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), TRUE)
ON CONFLICT (code) DO UPDATE
SET department_id = EXCLUDED.department_id,
    name = EXCLUDED.name,
    supervisor_employee_id = EXCLUDED.supervisor_employee_id,
    is_active = EXCLUDED.is_active;

INSERT INTO projects (id, name, code, status, start_date, end_date)
VALUES
    ('77777777-7777-7777-7777-777777777402', 'Internal Tools', 'INTERNAL-TOOLS', 'ACTIVE', CURRENT_DATE - INTERVAL '80 days', NULL),
    ('77777777-7777-7777-7777-777777777403', 'Infrastructure Upgrade', 'INFRA-UPGRADE', 'ACTIVE', CURRENT_DATE - INTERVAL '45 days', NULL)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    status = EXCLUDED.status,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date;

INSERT INTO team_hierarchy_relations (id, team_id, responsible_employee_id, collaborator_employee_id, status, start_date, end_date)
SELECT *
FROM (
    VALUES
        ('12121212-1212-1212-1212-121212121201'::uuid, '66666666-6666-6666-6666-666666666401'::uuid, NULL::uuid, '55555555-5555-5555-5555-555555555403'::uuid, 'ACTIVE', CURRENT_DATE - INTERVAL '120 days', NULL::date),
        ('12121212-1212-1212-1212-121212121202'::uuid, '66666666-6666-6666-6666-666666666401'::uuid, '55555555-5555-5555-5555-555555555403'::uuid, '55555555-5555-5555-5555-555555555404'::uuid, 'ACTIVE', CURRENT_DATE - INTERVAL '90 days', NULL::date),
        ('12121212-1212-1212-1212-121212121203'::uuid, '66666666-6666-6666-6666-666666666401'::uuid, '55555555-5555-5555-5555-555555555403'::uuid, '55555555-5555-5555-5555-555555555405'::uuid, 'ACTIVE', CURRENT_DATE - INTERVAL '60 days', NULL::date)
) AS seed(id, team_id, responsible_employee_id, collaborator_employee_id, status, start_date, end_date)
WHERE NOT EXISTS (
    SELECT 1
    FROM team_hierarchy_relations existing
    WHERE existing.id = seed.id
);

INSERT INTO validation_workflows (
    id, code, name, usage, validator_source, validation_mode, min_validators, fallback_mode,
    fallback_profile_id, fallback_permission_code, is_active, created_at, updated_at, is_default
)
VALUES
    ('13131313-1313-1313-1313-131313131301', 'LEAVE_ONE_REQUIRED', 'Leave One Required', 'LEAVE', 'TEAM_HIERARCHY', 'ONE_REQUIRED', NULL, 'HR_QUEUE', NULL, NULL, TRUE, NOW(), NOW(), TRUE),
    ('13131313-1313-1313-1313-131313131302', 'LEAVE_ALL_REQUIRED', 'Leave All Required', 'LEAVE', 'TEAM_HIERARCHY', 'ALL_REQUIRED', NULL, 'HR_QUEUE', NULL, NULL, TRUE, NOW(), NOW(), FALSE),
    ('13131313-1313-1313-1313-131313131303', 'LEAVE_MIN_TWO', 'Leave Minimum Two Validators', 'LEAVE', 'TEAM_HIERARCHY', 'MIN_N', 2, 'SPECIFIC_PROFILE', '88888888-8888-8888-8888-888888888803', NULL, TRUE, NOW(), NOW(), FALSE),
    ('13131313-1313-1313-1313-131313131304', 'LEAVE_INFO_PRIMARY', 'Leave Information Plus Primary', 'LEAVE', 'TEAM_HIERARCHY', 'INFO_PLUS_PRIMARY', NULL, 'SPECIFIC_PERMISSION', NULL, 'APPROVAL_STEP_READ', TRUE, NOW(), NOW(), FALSE)
ON CONFLICT (code) DO NOTHING;

UPDATE leave_types
SET validation_workflow_id = CASE
    WHEN code IN ('ANNUAL', 'SICK') THEN '13131313-1313-1313-1313-131313131301'::uuid
    WHEN code = 'UNPAID' THEN '13131313-1313-1313-1313-131313131302'::uuid
    WHEN code = 'EXCEPTIONAL' THEN '13131313-1313-1313-1313-131313131304'::uuid
    ELSE validation_workflow_id
END,
balance_tracked = CASE WHEN code = 'UNPAID' THEN FALSE ELSE TRUE END;

UPDATE enterprise_settings
SET default_validation_workflow_id = '13131313-1313-1313-1313-131313131301',
    active_calendar_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    monthly_acquisition_rate = 2,
    working_hours_per_day = 8,
    updated_at = NOW()
WHERE singleton_key = TRUE;

INSERT INTO leave_acquisition_policies (
    id, code, name, leave_type_id, frequency, monthly_rate, annual_quota, day_cap,
    acquisition_day, prorata_hire, negative_balance_allowed, start_date, end_date, is_active, created_at, updated_at
)
VALUES
    ('14141414-1414-1414-1414-141414141401', 'ANNUAL_MONTHLY', 'Monthly Annual Leave Policy', '22222222-2222-2222-2222-222222222201',
     'MONTHLY', 2, 24, 30, 1, TRUE, FALSE, CURRENT_DATE - INTERVAL '365 days', NULL, TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('99999999-9999-9999-9999-999999999403', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 5, 0, 2, 0),
    ('99999999-9999-9999-9999-999999999404', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 1, 2, 0, 0),
    ('99999999-9999-9999-9999-999999999405', (SELECT id FROM employees WHERE employee_code = 'EMP-003'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 0, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999406', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 3, 0, 1, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO NOTHING;

INSERT INTO leave_balance_transactions (id, employee_id, leave_type_id, transaction_type, amount, balance_after, source_type, source_id, comment, created_by_user_id, occurred_at)
VALUES
    ('15151515-1515-1515-1515-151515151501', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'ACCRUAL', 24, 24, 'ACQUISITION_POLICY', '14141414-1414-1414-1414-141414141401', 'Initial annual accrual', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '90 days'),
    ('15151515-1515-1515-1515-151515151502', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'MANUAL_ADJUSTMENT', 1, 25, 'MANUAL_ADJUSTMENT', NULL, 'Correction after manager review', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '20 days'),
    ('15151515-1515-1515-1515-151515151503', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'ACCRUAL', 24, 24, 'ACQUISITION_POLICY', '14141414-1414-1414-1414-141414141401', 'Initial annual accrual', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '60 days'),
    ('15151515-1515-1515-1515-151515151504', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), 'REQUEST_RESERVATION', -2, 22, 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0401', 'Reserved for pending request', (SELECT id FROM users WHERE email = 'product@demo.hris.local'), NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0402', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'ANNUAL'), CURRENT_DATE + 14, CURRENT_DATE + 15, 2, 'NORMAL', 'DRAFT', 'Draft demo request', NOW() - INTERVAL '5 hours', 0),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0403', (SELECT id FROM employees WHERE employee_code = 'EMP-003'), (SELECT id FROM leave_types WHERE code = 'SICK'), CURRENT_DATE - 12, CURRENT_DATE - 11, 2, 'URGENT', 'APPROVED', 'Approved sick leave', NOW() - INTERVAL '10 days', 0),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0404', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), (SELECT id FROM leave_types WHERE code = 'UNPAID'), CURRENT_DATE + 20, CURRENT_DATE + 21, 2, 'NORMAL', 'REJECTED', 'Rejected unpaid leave', NOW() - INTERVAL '8 days', 0),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0405', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), CURRENT_DATE + 3, CURRENT_DATE + 3, 1, 'NORMAL', 'CANCELLED', 'Cancelled exceptional leave', NOW() - INTERVAL '3 days', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_requests (
    id, request_number, requester_employee_id, requester_user_id, type_id, subject, description, status,
    submitted_at, due_at, created_at, updated_at, processed_by_user_id, reviewed_at, decided_at, completed_at, rejection_reason
)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddd0402', 'AR-DEMO-0002', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM users WHERE email = 'product@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'CERT_SALARY'), 'Salary certificate', 'Certificate needed for banking formalities', 'IN_REVIEW', NOW() - INTERVAL '3 days', NOW() + INTERVAL '1 day', NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '2 days', NULL, NULL, NULL),
    ('dddddddd-dddd-dddd-dddd-dddddddd0403', 'AR-DEMO-0003', (SELECT id FROM employees WHERE employee_code = 'EMP-003'), (SELECT id FROM users WHERE email = 'office@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'INFO_UPDATE'), 'Personal data update', 'Please update emergency contact details', 'APPROVED', NOW() - INTERVAL '6 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '6 days', NOW() - INTERVAL '2 days', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days', NULL, NULL),
    ('dddddddd-dddd-dddd-dddd-dddddddd0404', 'AR-DEMO-0004', (SELECT id FROM employees WHERE employee_code = 'EMP-004'), (SELECT id FROM users WHERE email = 'analyst@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'), 'Work certificate', 'Request rejected because attachment was missing', 'REJECTED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '7 days', NOW() - INTERVAL '4 days', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '6 days', NOW() - INTERVAL '4 days', NULL, 'Missing supporting context'),
    ('dddddddd-dddd-dddd-dddd-dddddddd0405', 'AR-DEMO-0005', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM users WHERE email = 'developer@demo.hris.local'), (SELECT id FROM admin_request_types WHERE code = 'CERT_SALARY'), 'Salary certificate archived', 'Completed sample request', 'COMPLETED', NOW() - INTERVAL '12 days', NOW() - INTERVAL '9 days', NOW() - INTERVAL '12 days', NOW() - INTERVAL '8 days', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '11 days', NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days', NULL)
ON CONFLICT (request_number) DO NOTHING;

INSERT INTO notifications (id, user_id, title, body, link_path, is_read, created_at)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0402', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'New leave request', 'A new leave request is waiting for your approval.', '/approvals', FALSE, NOW() - INTERVAL '2 hours'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0403', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'Administrative request in review', 'A back-office request has reached its review phase.', '/requests/inbox', FALSE, NOW() - INTERVAL '4 hours'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0404', (SELECT id FROM users WHERE email = 'product@demo.hris.local'), 'Leave request reserved', 'Your leave balance has been reserved for a pending request.', '/leave', TRUE, NOW() - INTERVAL '1 day'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0405', (SELECT id FROM users WHERE email = 'office@demo.hris.local'), 'Administrative request approved', 'Your personal data update has been approved.', '/requests', FALSE, NOW() - INTERVAL '2 days'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0406', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), 'Accrual run summary', 'The monthly accrual run completed with one warning.', '/settings/accrual-runs', FALSE, NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO leave_accrual_runs (id, run_date, started_at, finished_at, status, policies_processed, employees_processed, transactions_created, error_message, triggered_by, triggered_by_user_id)
VALUES
    ('16161616-1616-1616-1616-161616161601', CURRENT_DATE - INTERVAL '10 days', NOW() - INTERVAL '10 days 01:00:00', NOW() - INTERVAL '10 days 00:55:00', 'COMPLETED', 1, 4, 4, NULL, 'SYSTEM', NULL),
    ('16161616-1616-1616-1616-161616161602', CURRENT_DATE - INTERVAL '2 days', NOW() - INTERVAL '2 days 01:00:00', NOW() - INTERVAL '2 days 00:58:00', 'FAILED', 1, 3, 2, 'One employee balance was locked during processing', 'USER', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'))
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (id, actor_id, action, resource, resource_id, previous_state, new_state, ip_address, timestamp, actor_type)
VALUES
    ('17171717-1717-1717-1717-171717171701', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), 'ASSIGN', 'user_profile_assignment', (SELECT id FROM users WHERE email = 'product@demo.hris.local'), NULL, '{"profile":"SELF_SERVICE"}', '127.0.0.1', NOW() - INTERVAL '15 days', 'USER'),
    ('17171717-1717-1717-1717-171717171702', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'UPDATE', 'leave_balance', '99999999-9999-9999-9999-999999999404', '{"availableDays":24}', '{"availableDays":25}', '127.0.0.1', NOW() - INTERVAL '20 days', 'USER'),
    ('17171717-1717-1717-1717-171717171703', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'APPROVE', 'leave_request', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0403', '{"status":"IN_APPROVAL"}', '{"status":"APPROVED"}', '127.0.0.1', NOW() - INTERVAL '9 days', 'USER'),
    ('17171717-1717-1717-1717-171717171704', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), 'COMPLETE', 'admin_request', 'dddddddd-dddd-dddd-dddd-dddddddd0405', '{"status":"APPROVED"}', '{"status":"COMPLETED"}', '127.0.0.1', NOW() - INTERVAL '8 days', 'USER'),
    ('17171717-1717-1717-1717-171717171705', NULL, 'ACCRUAL_RUN', 'leave_accrual_run', '16161616-1616-1616-1616-161616161601', NULL, '{"status":"COMPLETED"}', NULL, NOW() - INTERVAL '10 days', 'SYSTEM')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT seed.user_id, ap.id, seed.assigned_at, seed.assigned_by_id, TRUE
FROM (
    VALUES
        ((SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:12:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'product@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:20:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'office@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:21:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local')),
        ((SELECT id FROM users WHERE email = 'analyst@demo.hris.local'), 'SELF_SERVICE', TIMESTAMPTZ '2026-01-01 09:22:00+00', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'))
) AS seed(user_id, profile_code, assigned_at, assigned_by_id)
JOIN access_profiles ap ON ap.code = seed.profile_code
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profile_assignments existing
    WHERE existing.user_id = seed.user_id
      AND existing.profile_id = ap.id
);

COMMIT;
