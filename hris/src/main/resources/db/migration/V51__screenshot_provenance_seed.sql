-- V51 - Screenshot-aligned seed: profile-assignment provenance + fresh dates
-- Purpose: cover the residual gaps left by V37 for the report screenshots:
--   * Sprint 1 / profile_assignment_rules console (auto-grant evidence)
--   * Sprint 1 / employee_detail_provenance (badge on EMP-001)
--   * Refresh a handful of timestamps so SLA badges and audit entries look
--     current relative to the demo date.
-- Idempotent: every insert is guarded by NOT EXISTS / ON CONFLICT DO NOTHING,
-- every update is restricted to the rows it owns.

-- ---------------------------------------------------------------------------
-- 1. Demonstrate the assignment engine fired (SYSTEM-source grants)
-- ---------------------------------------------------------------------------

-- 1a. Reclassify EMP-001's SELF_SERVICE grant as SYSTEM so the employee
--     detail provenance badge has something to render.
UPDATE user_profile_assignments
SET assignment_source = 'SYSTEM',
    source_event      = 'EMPLOYEE_ONBOARDED',
    source_ref_id     = (SELECT id FROM employees WHERE employee_code = 'EMP-001')
WHERE user_id = (SELECT id FROM users WHERE email = 'developer@demo.hris.local')
  AND profile_id = (SELECT id FROM access_profiles WHERE code = 'SELF_SERVICE')
  AND assignment_source = 'MANUAL';

-- 1b. Karim Jlassi (manager.engineering) becomes a department head -> the rule
--     for DEPT_HEAD_ASSIGNED grants DEPT_APPROVER_PROFILE. He does not have
--     this profile in the baseline demo, so the INSERT is safe.
INSERT INTO user_profile_assignments
    (user_id, profile_id, assigned_at, assigned_by_id, is_active,
     assignment_source, source_event, source_ref_id)
SELECT
    (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'),
    (SELECT id FROM access_profiles WHERE code = 'DEPT_APPROVER_PROFILE'),
    TIMESTAMPTZ '2026-02-15 09:00:00+00',
    NULL,
    TRUE,
    'SYSTEM',
    'DEPT_HEAD_ASSIGNED',
    (SELECT id FROM departments WHERE code = 'ENG')
WHERE NOT EXISTS (
    SELECT 1 FROM user_profile_assignments upa
    WHERE upa.user_id    = (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local')
      AND upa.profile_id = (SELECT id FROM access_profiles WHERE code = 'DEPT_APPROVER_PROFILE')
);

-- 1c. supervisor.operations is designated team head -> TEAM_APPROVER_PROFILE.
INSERT INTO user_profile_assignments
    (user_id, profile_id, assigned_at, assigned_by_id, is_active,
     assignment_source, source_event, source_ref_id)
SELECT
    u.id,
    (SELECT id FROM access_profiles WHERE code = 'TEAM_APPROVER_PROFILE'),
    TIMESTAMPTZ '2026-03-02 08:30:00+00',
    NULL,
    TRUE,
    'SYSTEM',
    'TEAM_HEAD_ASSIGNED',
    NULL
FROM users u
WHERE u.email = 'supervisor.operations@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1 FROM user_profile_assignments upa
      WHERE upa.user_id    = u.id
        AND upa.profile_id = (SELECT id FROM access_profiles WHERE code = 'TEAM_APPROVER_PROFILE')
  );

-- 1d. The director also auto-receives DEPT_APPROVER on dept-head designation
--     for the OPS department -- showcases a second department in the rules
--     console history.
INSERT INTO user_profile_assignments
    (user_id, profile_id, assigned_at, assigned_by_id, is_active,
     assignment_source, source_event, source_ref_id)
SELECT
    (SELECT id FROM users WHERE email = 'director@demo.hris.local'),
    (SELECT id FROM access_profiles WHERE code = 'DEPT_APPROVER_PROFILE'),
    TIMESTAMPTZ '2026-01-20 10:15:00+00',
    NULL,
    TRUE,
    'SYSTEM',
    'DEPT_HEAD_ASSIGNED',
    (SELECT id FROM departments WHERE code = 'OPS')
WHERE NOT EXISTS (
    SELECT 1 FROM user_profile_assignments upa
    WHERE upa.user_id    = (SELECT id FROM users WHERE email = 'director@demo.hris.local')
      AND upa.profile_id = (SELECT id FROM access_profiles WHERE code = 'DEPT_APPROVER_PROFILE')
);

-- ---------------------------------------------------------------------------
-- 2. Matching audit_log entries for the auto-grants
-- ---------------------------------------------------------------------------
-- audit_logs columns (post-V41): id, actor_id, action, resource, resource_id,
--   previous_state, new_state, ip_address, timestamp, actor_type.

INSERT INTO audit_logs
    (id, actor_id, action, resource, resource_id, previous_state, new_state,
     ip_address, timestamp, actor_type)
VALUES
    ('51515151-5151-5151-5151-000000000001',
     NULL,
     'SYSTEM_GRANT',
     'user_profile_assignment',
     NULL,
     NULL,
     '{"event":"EMPLOYEE_ONBOARDED","profile":"SELF_SERVICE","employee":"EMP-001"}',
     NULL,
     TIMESTAMPTZ '2026-01-05 09:00:00+00',
     'SYSTEM'),
    ('51515151-5151-5151-5151-000000000002',
     NULL,
     'SYSTEM_GRANT',
     'user_profile_assignment',
     NULL,
     NULL,
     '{"event":"DEPT_HEAD_ASSIGNED","profile":"DEPT_APPROVER_PROFILE","department":"ENG","employee":"EMP-MGR"}',
     NULL,
     TIMESTAMPTZ '2026-02-15 09:00:00+00',
     'SYSTEM'),
    ('51515151-5151-5151-5151-000000000003',
     NULL,
     'SYSTEM_GRANT',
     'user_profile_assignment',
     NULL,
     NULL,
     '{"event":"TEAM_HEAD_ASSIGNED","profile":"TEAM_APPROVER_PROFILE","team":"OPS-CORE"}',
     NULL,
     TIMESTAMPTZ '2026-03-02 08:30:00+00',
     'SYSTEM'),
    ('51515151-5151-5151-5151-000000000004',
     NULL,
     'SYSTEM_GRANT',
     'user_profile_assignment',
     NULL,
     NULL,
     '{"event":"DEPT_HEAD_ASSIGNED","profile":"DEPT_APPROVER_PROFILE","department":"OPS","employee":"EMP-DIR"}',
     NULL,
     TIMESTAMPTZ '2026-01-20 10:15:00+00',
     'SYSTEM')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Refresh admin-request SLA dates so the HR inbox always shows a mix
--    (breached / near-breach / healthy) relative to today.
-- ---------------------------------------------------------------------------
-- AR-2026-0012 = breached: submitted 12 days ago, due 9 days ago.
UPDATE admin_requests
SET submitted_at = NOW() - INTERVAL '12 days',
    due_at       = NOW() - INTERVAL '9 days'
WHERE request_number = 'AR-2026-0012';

-- AR-2026-0013 = healthy: submitted 2 days ago, due in 5 days.
UPDATE admin_requests
SET submitted_at = NOW() - INTERVAL '2 days',
    due_at       = NOW() + INTERVAL '5 days'
WHERE request_number = 'AR-2026-0013';

-- AR-2026-0014 = near-breach: submitted 6 days ago, due tomorrow.
UPDATE admin_requests
SET submitted_at = NOW() - INTERVAL '6 days',
    due_at       = NOW() + INTERVAL '1 day'
WHERE request_number = 'AR-2026-0014';

-- ---------------------------------------------------------------------------
-- 4. A recent audit-log entry per major resource, anchored to NOW() so the
--    audit log screen always shows fresh activity.
-- ---------------------------------------------------------------------------
INSERT INTO audit_logs
    (id, actor_id, action, resource, resource_id, previous_state, new_state,
     ip_address, timestamp, actor_type)
VALUES
    ('51515151-5151-5151-5151-000000000101',
     (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
     'UPDATE',
     'admin_request',
     NULL,
     '{"status":"SUBMITTED"}',
     '{"status":"IN_REVIEW"}',
     '10.0.4.18',
     NOW() - INTERVAL '2 hours',
     'USER'),
    ('51515151-5151-5151-5151-000000000102',
     (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'),
     'APPROVE',
     'leave_request',
     NULL,
     '{"status":"IN_APPROVAL"}',
     '{"status":"APPROVED"}',
     '10.0.4.55',
     NOW() - INTERVAL '5 hours',
     'USER'),
    ('51515151-5151-5151-5151-000000000103',
     (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),
     'UPDATE',
     'access_profile',
     NULL,
     '{"is_active":false}',
     '{"is_active":true}',
     '10.0.4.22',
     NOW() - INTERVAL '1 day',
     'USER'),
    ('51515151-5151-5151-5151-000000000104',
     NULL,
     'LOGIN_FAIL',
     'authentication',
     NULL,
     NULL,
     '{"reason":"INVALID_CREDENTIALS","attempts":3}',
     '85.214.36.7',
     NOW() - INTERVAL '3 hours',
     'SYSTEM')
ON CONFLICT (id) DO NOTHING;

COMMIT;
