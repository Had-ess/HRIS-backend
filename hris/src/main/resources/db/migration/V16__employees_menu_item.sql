-- Add Employees directory menu item and remove Users menu item

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES ('99999999-9999-9999-9999-999999999920', 'menu.administration.employees', 'menu.administration.employees', 'ADMINISTRATION', '/employees', 'users', 5, TRUE)
ON CONFLICT (code) DO UPDATE
SET route = EXCLUDED.route, icon = EXCLUDED.icon, display_order = EXCLUDED.display_order, is_active = EXCLUDED.is_active;

-- Deactivate Users menu item
UPDATE menu_items SET is_active = FALSE WHERE code = 'menu.administration.users';

-- Grant Employees menu item to HR_CONSOLE and ADMIN_CONSOLE
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code = 'menu.administration.employees'
WHERE profile.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
