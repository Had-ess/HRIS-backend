BEGIN;

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES ('55555555-5555-5555-5555-555555555400', 'ANALYTICS_READ', 'ANALYTICS', 'READ', 'GLOBAL',
        'Read analytics reports org-wide', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, perm.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions perm ON perm.name = 'ANALYTICS_READ'
WHERE profile.code IN ('MANAGER_INBOX', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

DELETE FROM profile_menu_access
WHERE profile_id = (SELECT id FROM access_profiles WHERE code = 'SELF_SERVICE')
  AND menu_item_id = (SELECT id FROM menu_items WHERE code = 'menu.workspace.reports');

DELETE FROM profile_permissions
WHERE profile_id = (SELECT id FROM access_profiles WHERE code = 'SELF_SERVICE')
  AND permission_id IN (
      SELECT id FROM permissions WHERE name IN ('ANALYTICS_READ_OWN', 'REPORT_READ')
  );

DELETE FROM profile_permissions
WHERE permission_id IN (
    SELECT id FROM permissions
    WHERE name IN ('ANALYTICS_READ_OWN', 'ANALYTICS_READ_SCOPED', 'ANALYTICS_READ_GLOBAL')
);

DELETE FROM permissions
WHERE name IN ('ANALYTICS_READ_OWN', 'ANALYTICS_READ_SCOPED', 'ANALYTICS_READ_GLOBAL');

UPDATE analytics_leave_metrics_snapshots         SET scope_type = 'GLOBAL', scope_id = NULL WHERE scope_type <> 'GLOBAL' OR scope_id IS NOT NULL;
UPDATE analytics_headcount_metrics_snapshots     SET scope_type = 'GLOBAL', scope_id = NULL WHERE scope_type <> 'GLOBAL' OR scope_id IS NOT NULL;
UPDATE analytics_leave_distribution_snapshots    SET scope_type = 'GLOBAL', scope_id = NULL WHERE scope_type <> 'GLOBAL' OR scope_id IS NOT NULL;
UPDATE analytics_approval_bottleneck_snapshots   SET scope_type = 'GLOBAL', scope_id = NULL WHERE scope_type <> 'GLOBAL' OR scope_id IS NOT NULL;

ALTER TABLE analytics_leave_metrics_snapshots         ALTER COLUMN scope_type SET DEFAULT 'GLOBAL';
ALTER TABLE analytics_headcount_metrics_snapshots     ALTER COLUMN scope_type SET DEFAULT 'GLOBAL';
ALTER TABLE analytics_leave_distribution_snapshots    ALTER COLUMN scope_type SET DEFAULT 'GLOBAL';
ALTER TABLE analytics_approval_bottleneck_snapshots   ALTER COLUMN scope_type SET DEFAULT 'GLOBAL';

COMMIT;
