BEGIN;

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES (
    '99999999-9999-9999-9999-999999999920',
    'menu.settings.accrualRuns',
    'menu.settings.accrualRuns',
    'SETTINGS',
    '/settings/accrual-runs',
    'calendar',
    70,
    TRUE
)
ON CONFLICT (code) DO UPDATE
SET translation_key = EXCLUDED.translation_key,
    section_code = EXCLUDED.section_code,
    route = EXCLUDED.route,
    icon = EXCLUDED.icon,
    display_order = EXCLUDED.display_order,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code = 'menu.settings.accrualRuns'
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

COMMIT;
