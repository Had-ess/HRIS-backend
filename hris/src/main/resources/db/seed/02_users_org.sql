-- Users, departments, employees, and role assignments.
-- Keycloak subject placeholders are intentionally obvious and should be replaced
-- in environments where the external IdP is active.
-- The backend provisioning flow auto-aligns these placeholders to the real
-- Keycloak subject on first login when the email matches.

BEGIN;

DELETE FROM departments d
WHERE d.code NOT IN ('ADMIN', 'HR', 'ENG', 'OPS', 'PRD')
  AND NOT EXISTS (
      SELECT 1 FROM employees e WHERE e.department_id = d.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.department_id = d.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM project_departments pd WHERE pd.department_id = d.id
  );

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_active, created_at, last_login)
VALUES
    ('33333333-3333-3333-3333-333333333301', 'KC_REPLACE_ADMINISTRATION', 'admin@demo.hris.local', 'Nadia', 'Ben Salem', 'fr', TRUE, NOW() - INTERVAL '180 days', NOW() - INTERVAL '2 hours'),
    ('33333333-3333-3333-3333-333333333302', 'KC_REPLACE_HR_ADMIN', 'hr.admin@demo.hris.local', 'Sami', 'Khadhraoui', 'fr', TRUE, NOW() - INTERVAL '150 days', NOW() - INTERVAL '3 hours'),
    ('33333333-3333-3333-3333-333333333303', 'KC_REPLACE_DIRECTOR', 'director@demo.hris.local', 'Leila', 'Mansour', 'en', TRUE, NOW() - INTERVAL '200 days', NOW() - INTERVAL '1 day'),
    ('33333333-3333-3333-3333-333333333304', 'KC_REPLACE_DEPT_MANAGER', 'manager.engineering@demo.hris.local', 'Karim', 'Jlassi', 'fr', TRUE, NOW() - INTERVAL '120 days', NOW() - INTERVAL '5 hours'),
    ('33333333-3333-3333-3333-333333333305', 'KC_REPLACE_PROJECT_SUPERVISOR', 'supervisor.operations@demo.hris.local', 'Amine', 'Zouari', 'fr', TRUE, NOW() - INTERVAL '110 days', NOW() - INTERVAL '7 hours'),
    ('33333333-3333-3333-3333-333333333306', 'KC_REPLACE_EMPLOYEE_DEV', 'developer@demo.hris.local', 'Yasmine', 'Trabelsi', 'en', TRUE, NOW() - INTERVAL '95 days', NOW() - INTERVAL '4 hours'),
    ('33333333-3333-3333-3333-333333333307', 'KC_REPLACE_EMPLOYEE_ANALYST', 'analyst@demo.hris.local', 'Walid', 'Mrad', 'fr', TRUE, NOW() - INTERVAL '90 days', NOW() - INTERVAL '8 hours'),
    ('33333333-3333-3333-3333-333333333308', 'KC_REPLACE_EMPLOYEE_PRODUCT', 'product@demo.hris.local', 'Rim', 'Ayedi', 'en', TRUE, NOW() - INTERVAL '30 days', NOW() - INTERVAL '6 hours'),
    ('33333333-3333-3333-3333-333333333309', 'KC_REPLACE_EMPLOYEE_OFFICE', 'office@demo.hris.local', 'Hedi', 'Gharbi', 'fr', TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '12 hours'),
    ('33333333-3333-3333-3333-333333333310', 'KC_REPLACE_EMPLOYEE_TERMINATED', 'former.employee@demo.hris.local', 'Mouna', 'Bouazizi', 'fr', FALSE, NOW() - INTERVAL '365 days', NOW() - INTERVAL '60 days')
ON CONFLICT (id) DO UPDATE
SET keycloak_id = EXCLUDED.keycloak_id,
    email = EXCLUDED.email,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    locale_preference = EXCLUDED.locale_preference,
    is_active = EXCLUDED.is_active,
    created_at = EXCLUDED.created_at,
    last_login = EXCLUDED.last_login;

INSERT INTO departments (id, name, code, head_employee_id, is_active)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'Administration', 'ADMIN', NULL, TRUE),
    ('22222222-2222-2222-2222-222222222202', 'Human Resources', 'HR', NULL, TRUE),
    ('22222222-2222-2222-2222-222222222203', 'Engineering', 'ENG', NULL, TRUE),
    ('22222222-2222-2222-2222-222222222204', 'Operations', 'OPS', NULL, TRUE),
    ('22222222-2222-2222-2222-222222222205', 'Product', 'PRD', NULL, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

INSERT INTO employees (id, user_id, employee_code, hire_date, job_title, status, contract_type, department_id, work_schedule_id)
VALUES
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333301', 'ADM-001', (CURRENT_DATE - INTERVAL '720 days')::date, 'Chief Administration Officer', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444402', '33333333-3333-3333-3333-333333333302', 'HR-001', (CURRENT_DATE - INTERVAL '540 days')::date, 'HR Administrator', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444403', '33333333-3333-3333-3333-333333333303', 'DIR-001', (CURRENT_DATE - INTERVAL '900 days')::date, 'Operations Director', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444404', '33333333-3333-3333-3333-333333333304', 'ENG-001', (CURRENT_DATE - INTERVAL '480 days')::date, 'Engineering Manager', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444405', '33333333-3333-3333-3333-333333333305', 'OPS-001', (CURRENT_DATE - INTERVAL '420 days')::date, 'Project Supervisor', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111102'),
    ('44444444-4444-4444-4444-444444444406', '33333333-3333-3333-3333-333333333306', 'ENG-002', (CURRENT_DATE - INTERVAL '250 days')::date, 'Frontend Engineer', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444407', '33333333-3333-3333-3333-333333333307', 'OPS-002', (CURRENT_DATE - INTERVAL '210 days')::date, 'Business Analyst', 'ACTIVE', 'FIXED_TERM', '22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111102'),
    ('44444444-4444-4444-4444-444444444408', '33333333-3333-3333-3333-333333333308', 'PRD-001', (date_trunc('month', CURRENT_DATE)::date + INTERVAL '2 days')::date, 'Product Owner', 'ACTIVE', 'PERMANENT', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444409', '33333333-3333-3333-3333-333333333309', 'HR-002', (date_trunc('month', CURRENT_DATE)::date + INTERVAL '5 days')::date, 'Office Coordinator', 'ACTIVE', 'FIXED_TERM', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101'),
    ('44444444-4444-4444-4444-444444444410', '33333333-3333-3333-3333-333333333310', 'HR-003', (CURRENT_DATE - INTERVAL '400 days')::date, 'Recruitment Assistant', 'TERMINATED', 'FIXED_TERM', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101')
ON CONFLICT (id) DO UPDATE
SET user_id = EXCLUDED.user_id,
    employee_code = EXCLUDED.employee_code,
    hire_date = EXCLUDED.hire_date,
    job_title = EXCLUDED.job_title,
    status = EXCLUDED.status,
    contract_type = EXCLUDED.contract_type,
    department_id = EXCLUDED.department_id,
    work_schedule_id = EXCLUDED.work_schedule_id;

UPDATE departments
SET head_employee_id = CASE code
    WHEN 'ADMIN' THEN '44444444-4444-4444-4444-444444444401'::uuid
    WHEN 'HR' THEN '44444444-4444-4444-4444-444444444402'::uuid
    WHEN 'ENG' THEN '44444444-4444-4444-4444-444444444404'::uuid
    WHEN 'OPS' THEN '44444444-4444-4444-4444-444444444405'::uuid
    WHEN 'PRD' THEN '44444444-4444-4444-4444-444444444403'::uuid
    ELSE head_employee_id
END
WHERE code IN ('ADMIN', 'HR', 'ENG', 'OPS', 'PRD');

-- Every seeded user can log in as an employee, and specific personas receive extra roles.
INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT '77777777-7777-7777-7777-777777777101', u.id, r.id, d.id, NOW() - INTERVAL '180 days', NULL, TRUE
FROM users u
JOIN roles r ON r.code = 'ADMINISTRATION'
JOIN departments d ON d.code = 'ADMIN'
WHERE u.email = 'admin@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT '77777777-7777-7777-7777-777777777102', u.id, r.id, d.id, NOW() - INTERVAL '150 days', NULL, TRUE
FROM users u
JOIN roles r ON r.code = 'HR_ADMIN'
JOIN departments d ON d.code = 'HR'
WHERE u.email = 'hr.admin@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT '77777777-7777-7777-7777-777777777103', u.id, r.id, d.id, NOW() - INTERVAL '200 days', NULL, TRUE
FROM users u
JOIN roles r ON r.code = 'DIRECTOR'
JOIN departments d ON d.code = 'PRD'
WHERE u.email = 'director@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT '77777777-7777-7777-7777-777777777104', u.id, r.id, d.id, NOW() - INTERVAL '120 days', NULL, TRUE
FROM users u
JOIN roles r ON r.code = 'DEPT_MANAGER'
JOIN departments d ON d.code = 'ENG'
WHERE u.email = 'manager.engineering@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT '77777777-7777-7777-7777-777777777105', u.id, r.id, d.id, NOW() - INTERVAL '110 days', NULL, TRUE
FROM users u
JOIN roles r ON r.code = 'PROJECT_SUPERVISOR'
JOIN departments d ON d.code = 'OPS'
WHERE u.email = 'supervisor.operations@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

INSERT INTO user_roles (id, user_id, role_id, department_id, assigned_at, expires_at, is_active)
SELECT
    CASE u.email
        WHEN 'admin@demo.hris.local' THEN '77777777-7777-7777-7777-777777777201'::uuid
        WHEN 'hr.admin@demo.hris.local' THEN '77777777-7777-7777-7777-777777777202'::uuid
        WHEN 'director@demo.hris.local' THEN '77777777-7777-7777-7777-777777777203'::uuid
        WHEN 'manager.engineering@demo.hris.local' THEN '77777777-7777-7777-7777-777777777204'::uuid
        WHEN 'supervisor.operations@demo.hris.local' THEN '77777777-7777-7777-7777-777777777205'::uuid
        WHEN 'developer@demo.hris.local' THEN '77777777-7777-7777-7777-777777777206'::uuid
        WHEN 'analyst@demo.hris.local' THEN '77777777-7777-7777-7777-777777777207'::uuid
        WHEN 'product@demo.hris.local' THEN '77777777-7777-7777-7777-777777777208'::uuid
        WHEN 'office@demo.hris.local' THEN '77777777-7777-7777-7777-777777777209'::uuid
        WHEN 'former.employee@demo.hris.local' THEN '77777777-7777-7777-7777-777777777210'::uuid
    END,
    u.id,
    r.id,
    e.department_id,
    NOW() - INTERVAL '90 days',
    NULL,
    TRUE
FROM users u
JOIN employees e ON e.user_id = u.id
JOIN roles r ON r.code = 'EMPLOYEE'
WHERE u.email IN (
    'admin@demo.hris.local',
    'hr.admin@demo.hris.local',
    'director@demo.hris.local',
    'manager.engineering@demo.hris.local',
    'supervisor.operations@demo.hris.local',
    'developer@demo.hris.local',
    'analyst@demo.hris.local',
    'product@demo.hris.local',
    'office@demo.hris.local',
    'former.employee@demo.hris.local'
)
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.is_active = TRUE
  );

-- Now that the administration actor exists, bind permissions to operational roles.
INSERT INTO role_permissions (id, role_id, permission_id, granted_at, granted_by_id)
SELECT
    gen_random_uuid(),
    r.id,
    p.id,
    NOW(),
    admin_user.id
FROM roles r
JOIN permissions p ON TRUE
JOIN users admin_user ON admin_user.email = 'admin@demo.hris.local'
WHERE r.code = 'ADMINISTRATION'
ON CONFLICT (role_id, permission_id) DO UPDATE
SET granted_at = EXCLUDED.granted_at,
    granted_by_id = EXCLUDED.granted_by_id;

INSERT INTO role_permissions (id, role_id, permission_id, granted_at, granted_by_id)
SELECT
    gen_random_uuid(),
    r.id,
    p.id,
    NOW(),
    admin_user.id
FROM roles r
JOIN permissions p ON p.name IN (
    'DASHBOARD_HR_VIEW',
    'DASHBOARD_DIRECTOR_VIEW',
    'ADMIN_REQUEST_PROCESS',
    'ADMIN_REQUEST_REJECT'
)
JOIN users admin_user ON admin_user.email = 'admin@demo.hris.local'
WHERE r.code = 'HR_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE
SET granted_at = EXCLUDED.granted_at,
    granted_by_id = EXCLUDED.granted_by_id;

INSERT INTO role_permissions (id, role_id, permission_id, granted_at, granted_by_id)
SELECT
    gen_random_uuid(),
    r.id,
    p.id,
    NOW(),
    admin_user.id
FROM roles r
JOIN permissions p ON p.name = 'DASHBOARD_DIRECTOR_VIEW'
JOIN users admin_user ON admin_user.email = 'admin@demo.hris.local'
WHERE r.code = 'DIRECTOR'
ON CONFLICT (role_id, permission_id) DO UPDATE
SET granted_at = EXCLUDED.granted_at,
    granted_by_id = EXCLUDED.granted_by_id;

COMMIT;
