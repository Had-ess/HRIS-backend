BEGIN;

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555140', 'TEAM_READ', 'TEAM', 'READ', 'SCOPED', 'Read teams', TRUE),
    ('55555555-5555-5555-5555-555555555141', 'TEAM_HIERARCHY_MANAGE', 'TEAM_HIERARCHY', 'MANAGE', 'SCOPED', 'Manage team hierarchy relations', TRUE),
    ('55555555-5555-5555-5555-555555555142', 'VALIDATION_WORKFLOW_READ', 'VALIDATION_WORKFLOW', 'READ', 'GLOBAL', 'Read validation workflows', TRUE),
    ('55555555-5555-5555-5555-555555555143', 'VALIDATION_WORKFLOW_MANAGE', 'VALIDATION_WORKFLOW', 'MANAGE', 'GLOBAL', 'Manage validation workflows', TRUE)
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
    'TEAM_READ',
    'TEAM_HIERARCHY_MANAGE',
    'VALIDATION_WORKFLOW_READ',
    'VALIDATION_WORKFLOW_MANAGE'
)
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'TEAM_READ',
    'TEAM_HIERARCHY_MANAGE',
    'VALIDATION_WORKFLOW_READ',
    'VALIDATION_WORKFLOW_MANAGE'
)
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

COMMIT;
