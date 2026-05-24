INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code IN (
    'menu.administration.employees',
    'menu.administration.teams',
    'menu.settings.projects'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'DEPARTMENT_READ',
    'EMPLOYEE_READ',
    'TEAM_READ',
    'TEAM_MANAGE',
    'PROJECT_ASSIGNMENT_MANAGE',
    'PROJECT_PORTFOLIO_MANAGE'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;
