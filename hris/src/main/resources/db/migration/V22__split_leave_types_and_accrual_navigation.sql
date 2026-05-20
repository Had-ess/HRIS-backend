-- Split leave types, accrual types, and accrual history into separate navigation entries.
-- Preserve existing menu access grants and reuse the existing accrual-runs menu for history.

BEGIN;

UPDATE menu_items
SET translation_key = 'menu.configuration.leaveTypes',
    route = '/settings/leave-types',
    icon = 'calendar',
    display_order = 10,
    section_code = 'CONFIGURATION'
WHERE code = 'menu.settings.leaveTypesAcquisition';

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES (
    '99999999-9999-9999-9999-999999999921',
    'menu.settings.accrualTypes',
    'menu.configuration.accrualTypes',
    'CONFIGURATION',
    '/settings/accrual-types',
    'calendar',
    20,
    TRUE
)
ON CONFLICT (code) DO UPDATE
SET translation_key = EXCLUDED.translation_key,
    section_code = EXCLUDED.section_code,
    route = EXCLUDED.route,
    icon = EXCLUDED.icon,
    display_order = EXCLUDED.display_order,
    is_active = EXCLUDED.is_active;

UPDATE menu_items
SET translation_key = 'menu.configuration.accrualHistory',
    route = '/settings/accrual-history',
    icon = 'calendar',
    display_order = 30,
    section_code = 'CONFIGURATION'
WHERE code = 'menu.settings.accrualRuns';

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code = 'menu.settings.accrualTypes'
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

COMMIT;
