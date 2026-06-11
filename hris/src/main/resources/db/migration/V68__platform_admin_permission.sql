-- =============================================================================
-- V68: PLATFORM_ADMIN permission — guards tenant provisioning
-- =============================================================================
-- Held only by the DEFAULT tenant's ADMIN_CONSOLE profile (interim platform
-- operator until a real platform console exists). Tenant provisioning clones
-- the default tenant's profile grants but explicitly excludes this permission,
-- so customer-tenant admins can never reach /api/platform/**.

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES ('55555555-5555-5555-5555-555555555990', 'PLATFORM_ADMIN', 'PLATFORM', 'ADMIN',
        'GLOBAL', 'Provision and manage tenants (platform operator)', TRUE)
ON CONFLICT (name) DO NOTHING;

-- Flyway runs without a tenant session setting, so tenant_id is explicit.
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, '00000000-0000-0000-0000-000000000001'
FROM access_profiles p
JOIN permissions perm ON perm.name = 'PLATFORM_ADMIN'
WHERE p.code = 'ADMIN_CONSOLE'
  AND p.tenant_id = '00000000-0000-0000-0000-000000000001'
ON CONFLICT (profile_id, permission_id) DO NOTHING;
