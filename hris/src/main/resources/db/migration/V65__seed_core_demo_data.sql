-- Consolidated demo seed (recovered from the merged branch's V56, adapted
-- post-Keycloak-decommission: no keycloak_id column, tenant-composite
-- conflict targets; tenant_id comes from the column default). Idempotent.
BEGIN;

-- ============================================================================
-- Demo users (credentials attach via DemoCredentialSeeder when
-- app.auth.demo-seed.enabled is true — local profile only)
-- ============================================================================

INSERT INTO users (id, email, first_name, last_name, locale_preference, is_seed, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333301', 'admin@demo.hris.local',              'Nadia',  'Ben Salem',   'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333302', 'hr.admin@demo.hris.local',            'Sami',   'Khadhraoui',  'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333303', 'manager.engineering@demo.hris.local', 'Karim',  'Jlassi',      'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333304', 'developer@demo.hris.local',           'Yasmine','Trabelsi',    'en', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333305', 'product@demo.hris.local',             'Rim',    'Ayedi',       'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333306', 'office@demo.hris.local',              'Hedi',   'Gharbi',      'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333307', 'analyst@demo.hris.local',             'Walid',  'Mrad',        'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333308', 'supervisor.operations@demo.hris.local','Amine',  'Zouari',      'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333309', 'director@demo.hris.local',            'Fawzi',  'Drissi',      'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333310', 'qa@demo.hris.local',                  'Ines',   'Karoui',      'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333311', 'legal@demo.hris.local',               'Hela',   'Nasri',       'fr', TRUE, TRUE),
    ('33333333-3333-3333-3333-333333333312', 'finance.viewer@demo.hris.local',      'Mehdi',  'Saadi',       'en', TRUE, TRUE)
ON CONFLICT (tenant_id, email) DO UPDATE
SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    locale_preference = EXCLUDED.locale_preference,
    is_seed = EXCLUDED.is_seed,
    is_active = EXCLUDED.is_active;

-- ============================================================================
-- Core departments (needed by employees below)
-- ============================================================================

INSERT INTO departments (id, name, code, head_employee_id, is_active)
VALUES
    ('44444444-4444-4444-4444-444444444401', 'Engineering', 'ENG', NULL, TRUE),
    ('44444444-4444-4444-4444-444444444402', 'Operations', 'OPS', NULL, TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ============================================================================
-- Employees
-- ============================================================================

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
FROM users u WHERE u.email = 'admin@demo.hris.local'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);

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
FROM users u WHERE u.email = 'hr.admin@demo.hris.local'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);

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
FROM users u WHERE u.email = 'manager.engineering@demo.hris.local'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);

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
FROM users u WHERE u.email = 'developer@demo.hris.local'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);

-- Set supervisor relationships
UPDATE employees
SET supervisor_employee_id = (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
WHERE employee_code IN ('EMP-ADMIN');

-- ============================================================================
-- Profile assignments
-- ============================================================================

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email = 'admin@demo.hris.local'
  AND ap.code = 'ADMIN_CONSOLE'
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email = 'hr.admin@demo.hris.local'
  AND ap.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE', 'SELF_SERVICE')
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email = 'manager.engineering@demo.hris.local'
  AND ap.code = 'SELF_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email IN ('developer@demo.hris.local', 'product@demo.hris.local', 'office@demo.hris.local',
                  'analyst@demo.hris.local', 'qa@demo.hris.local', 'legal@demo.hris.local',
                  'finance.viewer@demo.hris.local', 'supervisor.operations@demo.hris.local')
  AND ap.code = 'SELF_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email = 'director@demo.hris.local'
  AND ap.code = 'ADMIN_CONSOLE'
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT u.id, ap.id, NOW(), NULL, TRUE
FROM users u
CROSS JOIN access_profiles ap
WHERE u.email = 'finance.viewer@demo.hris.local'
  AND ap.code = 'HR_CONSOLE'
  AND NOT EXISTS (SELECT 1 FROM user_profile_assignments pa WHERE pa.user_id = u.id AND pa.profile_id = ap.id);

UPDATE departments
SET head_employee_id = CASE
    WHEN code = 'ENG' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-MGR')
    WHEN code = 'OPS' THEN (SELECT id FROM employees WHERE employee_code = 'EMP-HR')
    ELSE head_employee_id
END
WHERE code IN ('ENG', 'OPS');

COMMIT;
