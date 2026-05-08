INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555144', 'LEAVE_TYPE_READ', 'LEAVE_TYPE', 'READ', 'GLOBAL', 'Read leave types', TRUE),
    ('55555555-5555-5555-5555-555555555145', 'LEAVE_REQUEST_APPROVE', 'LEAVE_REQUEST', 'APPROVE', 'OWN', 'Approve assigned leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555146', 'LEAVE_REQUEST_FALLBACK_APPROVE', 'LEAVE_REQUEST', 'FALLBACK_APPROVE', 'GLOBAL', 'Receive fallback leave approvals', TRUE)
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
    'LEAVE_TYPE_READ',
    'LEAVE_REQUEST_APPROVE'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_TYPE_READ',
    'LEAVE_REQUEST_APPROVE',
    'LEAVE_REQUEST_FALLBACK_APPROVE'
)
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_TYPE_READ',
    'LEAVE_REQUEST_APPROVE',
    'LEAVE_REQUEST_FALLBACK_APPROVE'
)
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name = 'LEAVE_TYPE_READ'
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;
