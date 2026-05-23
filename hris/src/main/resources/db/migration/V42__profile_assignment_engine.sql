-- Feature 1: Automated Profile Assignment Engine
-- Adds provenance columns to user_profile_assignments, introduces the
-- profile_assignment_rules registry, and seeds the two new system profiles
-- (TEAM_APPROVER_PROFILE, DEPT_APPROVER_PROFILE) plus the default rule set
-- covering the ten structural events that drive automatic provisioning.

-- 1. Provenance columns on user_profile_assignments ----------------------------
ALTER TABLE user_profile_assignments
    ADD COLUMN IF NOT EXISTS assignment_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS source_event     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_ref_id    UUID;

ALTER TABLE user_profile_assignments
    DROP CONSTRAINT IF EXISTS ck_user_profile_assignments_source;
ALTER TABLE user_profile_assignments
    ADD CONSTRAINT ck_user_profile_assignments_source
    CHECK (assignment_source IN ('MANUAL', 'SYSTEM'));

CREATE INDEX IF NOT EXISTS idx_user_profile_assignments_source
    ON user_profile_assignments (assignment_source, source_event);

-- 2. New system profiles -------------------------------------------------------
INSERT INTO access_profiles (id, code, display_key, description_key, is_system_profile, is_active)
VALUES
    ('88888888-8888-8888-8888-888888888805', 'TEAM_APPROVER_PROFILE',
        'profile.teamApprover', 'profile.teamApprover.description', TRUE, TRUE),
    ('88888888-8888-8888-8888-888888888806', 'DEPT_APPROVER_PROFILE',
        'profile.deptApprover', 'profile.deptApprover.description', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- Grant the two new system profiles the minimum permissions required for the
-- approval inbox (read approval steps + read leave requests). The matrix is
-- additive: when a structural rule grants TEAM_APPROVER_PROFILE, the user
-- inherits these permissions and can therefore see and act on their inbox.
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
CROSS JOIN permissions permission
WHERE profile.code IN ('TEAM_APPROVER_PROFILE', 'DEPT_APPROVER_PROFILE')
  AND permission.name IN ('APPROVAL_STEP_READ', 'APPROVAL_STEP_DECIDE', 'LEAVE_REQUEST_READ')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- 3. Profile assignment rules registry ----------------------------------------
CREATE TABLE IF NOT EXISTS profile_assignment_rules (
    id              UUID PRIMARY KEY,
    trigger_event   VARCHAR(80) NOT NULL,
    profile_id      UUID NOT NULL REFERENCES access_profiles(id) ON DELETE RESTRICT,
    action          VARCHAR(20) NOT NULL,
    scope_strategy  VARCHAR(20) NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 100,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_profile_assignment_rules_action
        CHECK (action IN ('GRANT', 'REVOKE')),
    CONSTRAINT ck_profile_assignment_rules_scope
        CHECK (scope_strategy IN ('FROM_EVENT', 'GLOBAL'))
);

CREATE INDEX IF NOT EXISTS idx_profile_assignment_rules_trigger
    ON profile_assignment_rules (trigger_event, is_active, priority);

-- 4. Default rule set ---------------------------------------------------------
-- Each rule references one of the seeded system profiles by id so the
-- engine can resolve the target profile without an extra lookup at runtime.
INSERT INTO profile_assignment_rules
    (id, trigger_event, profile_id, action, scope_strategy, priority, is_active, description)
VALUES
    -- Onboarding / offboarding
    ('77777777-7777-7777-7777-777777777701',
     'EMPLOYEE_ONBOARDED',
     '88888888-8888-8888-8888-888888888801', 'GRANT', 'FROM_EVENT', 10, TRUE,
     'Grant SELF_SERVICE to every newly onboarded employee.'),
    ('77777777-7777-7777-7777-777777777702',
     'EMPLOYEE_OFFBOARDED',
     '88888888-8888-8888-8888-888888888801', 'REVOKE', 'FROM_EVENT', 10, TRUE,
     'Revoke SELF_SERVICE when an employee is offboarded.'),
    ('77777777-7777-7777-7777-777777777703',
     'EMPLOYEE_OFFBOARDED',
     '88888888-8888-8888-8888-888888888805', 'REVOKE', 'FROM_EVENT', 20, TRUE,
     'Revoke TEAM_APPROVER_PROFILE on offboarding.'),
    ('77777777-7777-7777-7777-777777777704',
     'EMPLOYEE_OFFBOARDED',
     '88888888-8888-8888-8888-888888888806', 'REVOKE', 'FROM_EVENT', 30, TRUE,
     'Revoke DEPT_APPROVER_PROFILE on offboarding.'),

    -- Department head lifecycle
    ('77777777-7777-7777-7777-777777777705',
     'DEPT_HEAD_ASSIGNED',
     '88888888-8888-8888-8888-888888888806', 'GRANT', 'FROM_EVENT', 10, TRUE,
     'Grant DEPT_APPROVER_PROFILE to the designated department head.'),
    ('77777777-7777-7777-7777-777777777706',
     'DEPT_HEAD_REMOVED',
     '88888888-8888-8888-8888-888888888806', 'REVOKE', 'FROM_EVENT', 10, TRUE,
     'Revoke DEPT_APPROVER_PROFILE when a department head is removed.'),

    -- Team head lifecycle
    ('77777777-7777-7777-7777-777777777707',
     'TEAM_HEAD_ASSIGNED',
     '88888888-8888-8888-8888-888888888805', 'GRANT', 'FROM_EVENT', 10, TRUE,
     'Grant TEAM_APPROVER_PROFILE to a designated team head.'),
    ('77777777-7777-7777-7777-777777777708',
     'TEAM_HEAD_REMOVED',
     '88888888-8888-8888-8888-888888888805', 'REVOKE', 'FROM_EVENT', 10, TRUE,
     'Revoke TEAM_APPROVER_PROFILE when a team head designation ends.'),

    -- Generic approver designation (kept distinct from team-head lifecycle so
    -- ad-hoc approval grants can be retired separately)
    ('77777777-7777-7777-7777-777777777709',
     'APPROVER_DESIGNATED',
     '88888888-8888-8888-8888-888888888805', 'GRANT', 'FROM_EVENT', 10, TRUE,
     'Grant TEAM_APPROVER_PROFILE to a non-head approver.'),
    ('77777777-7777-7777-7777-77777777770A',
     'APPROVER_REMOVED',
     '88888888-8888-8888-8888-888888888805', 'REVOKE', 'FROM_EVENT', 10, TRUE,
     'Revoke TEAM_APPROVER_PROFILE when an ad-hoc approver designation ends.'),

    -- Project leadership
    ('77777777-7777-7777-7777-77777777770B',
     'PROJECT_LEAD_ASSIGNED',
     '88888888-8888-8888-8888-888888888805', 'GRANT', 'FROM_EVENT', 10, TRUE,
     'Grant TEAM_APPROVER_PROFILE to the assigned project lead (PROJECT_MANAGER).'),
    ('77777777-7777-7777-7777-77777777770C',
     'PROJECT_LEAD_REMOVED',
     '88888888-8888-8888-8888-888888888805', 'REVOKE', 'FROM_EVENT', 10, TRUE,
     'Revoke TEAM_APPROVER_PROFILE when a project lead assignment ends.')
ON CONFLICT (id) DO NOTHING;
