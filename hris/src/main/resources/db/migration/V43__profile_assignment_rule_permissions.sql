-- Feature 3: REST endpoints for profile assignment rules
-- Adds the dedicated permissions used to gate read + manage flows on the
-- profile_assignment_rules table, then grants them to the ADMIN_CONSOLE
-- profile so existing administrators inherit access automatically.

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555901', 'PROFILE_ASSIGNMENT_RULE_READ',
        'PROFILE_ASSIGNMENT_RULE', 'READ', 'GLOBAL',
        'View automatic profile assignment rules', TRUE),
    ('55555555-5555-5555-5555-555555555902', 'PROFILE_ASSIGNMENT_RULE_MANAGE',
        'PROFILE_ASSIGNMENT_RULE', 'MANAGE', 'GLOBAL',
        'Edit automatic profile assignment rules (priority, scope, activation)', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
CROSS JOIN permissions permission
WHERE profile.code = 'ADMIN_CONSOLE'
  AND permission.name IN ('PROFILE_ASSIGNMENT_RULE_READ', 'PROFILE_ASSIGNMENT_RULE_MANAGE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;
