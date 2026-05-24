INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'EMPLOYEE_READ',
    'EMPLOYEE_MANAGE',
    'DEPARTMENT_READ',
    'DEPARTMENT_MANAGE',
    'TEAM_READ',
    'TEAM_MANAGE',
    'PROJECT_ASSIGNMENT_MANAGE',
    'PROJECT_PORTFOLIO_MANAGE'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;
