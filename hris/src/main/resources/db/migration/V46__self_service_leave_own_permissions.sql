-- Ensure employee/self-service leave access uses explicit OWN permissions.
-- Kept additive and idempotent to avoid breaking existing profile customizations.

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555155', 'LEAVE_REQUEST_CREATE', 'LEAVE_REQUEST', 'CREATE', 'OWN', 'Create own leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555156', 'LEAVE_REQUEST_READ_OWN', 'LEAVE_REQUEST', 'READ_OWN', 'OWN', 'Read own leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555157', 'LEAVE_REQUEST_CANCEL_OWN', 'LEAVE_REQUEST', 'CANCEL_OWN', 'OWN', 'Cancel own leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555158', 'LEAVE_REQUEST_READ_GLOBAL', 'LEAVE_REQUEST', 'READ_GLOBAL', 'GLOBAL', 'Read all leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555159', 'LEAVE_REQUEST_MANAGE', 'LEAVE_REQUEST', 'MANAGE', 'GLOBAL', 'Manage all leave requests', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_CREATE',
    'LEAVE_REQUEST_READ_OWN',
    'LEAVE_REQUEST_CANCEL_OWN',
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_TYPE_READ'
)
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_CREATE',
    'LEAVE_REQUEST_READ_OWN',
    'LEAVE_REQUEST_CANCEL_OWN',
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_TYPE_READ'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_CREATE',
    'LEAVE_REQUEST_READ_OWN',
    'LEAVE_REQUEST_CANCEL_OWN',
    'LEAVE_REQUEST_READ_GLOBAL',
    'LEAVE_REQUEST_MANAGE'
)
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;
