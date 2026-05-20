BEGIN;

-- 1. Update the route and translation key of the old accrual-runs menu item
UPDATE menu_items
SET route = '/settings/accrual-history',
    translation_key = 'menu.configuration.accrualHistory'
WHERE code = 'menu.settings.accrualRuns';

-- 2. Insert the new menu item for Acquisition Policies
INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES (
    '99999999-9999-9999-9999-999999999925',
    'menu.settings.acquisitionPolicies',
    'menu.configuration.acquisitionPolicies',
    'CONFIGURATION',
    '/settings/acquisition-policies',
    'calendar',
    15,
    TRUE
)
ON CONFLICT (code) DO UPDATE
SET translation_key = EXCLUDED.translation_key,
    section_code = EXCLUDED.section_code,
    route = EXCLUDED.route,
    icon = EXCLUDED.icon,
    display_order = EXCLUDED.display_order,
    is_active = EXCLUDED.is_active;

-- 3. Grant access to the new menu item for HR_CONSOLE and ADMIN_CONSOLE profiles
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code = 'menu.settings.acquisitionPolicies'
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

COMMIT;
