-- =============================================================================
-- V80 — Compensation Phase 4: total rewards + analytics (menus only)
-- =============================================================================
-- Phase 4 is entirely read-only and computed live over existing Phase 1-3 data,
-- so there are no new tables and no new permissions. This migration only seeds
-- the two navigation entries and fans the grants out to existing tenants
-- (new tenants inherit them via TenantProvisioningService's profile_menu_access
-- clone). Self statement reuses COMPENSATION_VIEW_OWN; the analytics dashboard
-- reuses COMPENSATION_MANAGE.

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999959', 'menu.workspace.totalRewards',
        'menu.workspace.totalRewards', 'WORKSPACE', '/compensation/total-rewards', 'gift', 46, TRUE),
    ('99999999-9999-9999-9999-999999999960', 'menu.insights.compAnalytics',
        'menu.insights.compAnalytics', 'INSIGHTS', '/compensation/analytics', 'chart-bar', 15, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Total-rewards statement to every profile (self-view); analytics to HR/admin.
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.totalRewards'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.insights.compAnalytics'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
