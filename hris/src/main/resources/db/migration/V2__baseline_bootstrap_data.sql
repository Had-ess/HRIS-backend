BEGIN;

INSERT INTO work_schedules (id, name, working_days, hours_per_day)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'Standard 40h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 8),
    ('11111111-1111-1111-1111-111111111102', 'Support 35h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 7)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public_holidays (id, date, name, is_recurring)
VALUES
    ('11111111-1111-1111-1111-111111111201', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 1, 1), 'New Year''s Day', TRUE),
    ('11111111-1111-1111-1111-111111111202', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 1), 'Labour Day', TRUE),
    ('11111111-1111-1111-1111-111111111203', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 12, 17), 'Revolution Day', TRUE)
ON CONFLICT (id) DO UPDATE
SET date = EXCLUDED.date,
    name = EXCLUDED.name,
    is_recurring = EXCLUDED.is_recurring;

INSERT INTO leave_types (id, code, name, is_paid, requires_justification, is_active)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'ANNUAL', 'Annual Leave', TRUE, FALSE, TRUE),
    ('22222222-2222-2222-2222-222222222202', 'SICK', 'Sick Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222203', 'MATERNITY', 'Maternity Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222204', 'PATERNITY', 'Paternity Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222205', 'EXCEPTIONAL', 'Exceptional Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222206', 'UNPAID', 'Unpaid Leave', FALSE, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_paid = EXCLUDED.is_paid,
    requires_justification = EXCLUDED.requires_justification,
    is_active = EXCLUDED.is_active;

INSERT INTO admin_request_types (id, code, name, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333201', 'CERT_WORK', 'Work Certificate', TRUE),
    ('33333333-3333-3333-3333-333333333202', 'CERT_SALARY', 'Salary Certificate', TRUE),
    ('33333333-3333-3333-3333-333333333203', 'INFO_UPDATE', 'Personal Information Update', TRUE),
    ('33333333-3333-3333-3333-333333333204', 'EQUIPMENT', 'Equipment Request', TRUE),
    ('33333333-3333-3333-3333-333333333205', 'OTHER', 'Other', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555101', 'USER_READ', 'USER', 'READ', 'GLOBAL', 'View users', TRUE),
    ('55555555-5555-5555-5555-555555555102', 'USER_CREATE', 'USER', 'CREATE', 'GLOBAL', 'Create users', TRUE),
    ('55555555-5555-5555-5555-555555555103', 'USER_DELETE', 'USER', 'DELETE', 'GLOBAL', 'Delete users', TRUE),
    ('55555555-5555-5555-5555-555555555104', 'USER_ASSIGN_PROFILE', 'USER', 'ASSIGN_PROFILE', 'GLOBAL', 'Assign access profiles to users', TRUE),
    ('55555555-5555-5555-5555-555555555105', 'PERMISSION_READ', 'PERMISSION', 'READ', 'GLOBAL', 'View permissions', TRUE),
    ('55555555-5555-5555-5555-555555555106', 'PERMISSION_CREATE', 'PERMISSION', 'CREATE', 'GLOBAL', 'Create permissions', TRUE),
    ('55555555-5555-5555-5555-555555555107', 'PERMISSION_UPDATE', 'PERMISSION', 'UPDATE', 'GLOBAL', 'Update permissions', TRUE),
    ('55555555-5555-5555-5555-555555555108', 'PERMISSION_DELETE', 'PERMISSION', 'DELETE', 'GLOBAL', 'Delete permissions', TRUE),
    ('55555555-5555-5555-5555-555555555109', 'ACCESS_PROFILE_READ', 'ACCESS_PROFILE', 'READ', 'GLOBAL', 'View access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555110', 'ACCESS_PROFILE_CREATE', 'ACCESS_PROFILE', 'CREATE', 'GLOBAL', 'Create access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555111', 'ACCESS_PROFILE_UPDATE', 'ACCESS_PROFILE', 'UPDATE', 'GLOBAL', 'Update access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555112', 'ACCESS_PROFILE_DELETE', 'ACCESS_PROFILE', 'DELETE', 'GLOBAL', 'Deactivate access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555113', 'ACCESS_PROFILE_ASSIGN_PERMISSION', 'ACCESS_PROFILE', 'ASSIGN_PERMISSION', 'GLOBAL', 'Assign permissions to access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555114', 'ACCESS_PROFILE_ASSIGN_MENU', 'ACCESS_PROFILE', 'ASSIGN_MENU', 'GLOBAL', 'Assign menus to access profiles', TRUE),
    ('55555555-5555-5555-5555-555555555115', 'MENU_ITEM_READ', 'MENU_ITEM', 'READ', 'GLOBAL', 'View menu items', TRUE),
    ('55555555-5555-5555-5555-555555555116', 'EMPLOYEE_READ', 'EMPLOYEE', 'READ', 'SCOPED', 'Read employee records', TRUE),
    ('55555555-5555-5555-5555-555555555117', 'EMPLOYEE_MANAGE', 'EMPLOYEE', 'MANAGE', 'GLOBAL', 'Create and update employees', TRUE),
    ('55555555-5555-5555-5555-555555555118', 'EMPLOYEE_DELETE', 'EMPLOYEE', 'DELETE', 'GLOBAL', 'Delete employees', TRUE),
    ('55555555-5555-5555-5555-555555555119', 'DEPARTMENT_READ', 'DEPARTMENT', 'READ', 'SCOPED', 'Read departments', TRUE),
    ('55555555-5555-5555-5555-555555555120', 'DEPARTMENT_MANAGE', 'DEPARTMENT', 'MANAGE', 'GLOBAL', 'Manage departments', TRUE),
    ('55555555-5555-5555-5555-555555555121', 'PROJECT_PORTFOLIO_MANAGE', 'PROJECT', 'PORTFOLIO_MANAGE', 'GLOBAL', 'Manage project portfolio', TRUE),
    ('55555555-5555-5555-5555-555555555122', 'PROJECT_ASSIGNMENT_MANAGE', 'PROJECT', 'ASSIGNMENT_MANAGE', 'SCOPED', 'Manage project assignments', TRUE),
    ('55555555-5555-5555-5555-555555555123', 'TEAM_MANAGE', 'TEAM', 'MANAGE', 'SCOPED', 'Manage teams', TRUE),
    ('55555555-5555-5555-5555-555555555124', 'LEAVE_REQUEST_READ', 'LEAVE_REQUEST', 'READ', 'SCOPED', 'Read leave requests', TRUE),
    ('55555555-5555-5555-5555-555555555125', 'LEAVE_BALANCE_READ', 'LEAVE_BALANCE', 'READ', 'SCOPED', 'Read leave balances', TRUE),
    ('55555555-5555-5555-5555-555555555126', 'LEAVE_TYPE_MANAGE', 'LEAVE_TYPE', 'MANAGE', 'GLOBAL', 'Manage leave types', TRUE),
    ('55555555-5555-5555-5555-555555555127', 'APPROVAL_STEP_READ', 'APPROVAL_STEP', 'READ', 'OWN', 'Read assigned approval steps', TRUE),
    ('55555555-5555-5555-5555-555555555128', 'APPROVAL_STEP_DECIDE', 'APPROVAL_STEP', 'DECIDE', 'OWN', 'Approve or reject assigned approval steps', TRUE),
    ('55555555-5555-5555-5555-555555555129', 'ADMIN_REQUEST_INBOX_READ', 'ADMIN_REQUEST', 'INBOX_READ', 'GLOBAL', 'Read administration request inbox', TRUE),
    ('55555555-5555-5555-5555-555555555130', 'ADMIN_REQUEST_PROCESS', 'ADMIN_REQUEST', 'PROCESS', 'GLOBAL', 'Process administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555131', 'ADMIN_REQUEST_APPROVE', 'ADMIN_REQUEST', 'APPROVE', 'GLOBAL', 'Approve administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555132', 'ADMIN_REQUEST_REJECT', 'ADMIN_REQUEST', 'REJECT', 'GLOBAL', 'Reject administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555133', 'ADMIN_REQUEST_COMPLETE', 'ADMIN_REQUEST', 'COMPLETE', 'GLOBAL', 'Complete administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555134', 'ADMIN_REQUEST_TYPE_MANAGE', 'ADMIN_REQUEST_TYPE', 'MANAGE', 'GLOBAL', 'Manage administration request types', TRUE),
    ('55555555-5555-5555-5555-555555555135', 'DASHBOARD_SUPERVISOR_VIEW', 'DASHBOARD', 'SUPERVISOR_VIEW', 'SCOPED', 'Open the manager dashboard', TRUE),
    ('55555555-5555-5555-5555-555555555136', 'DASHBOARD_HR_VIEW', 'DASHBOARD', 'HR_VIEW', 'GLOBAL', 'Open the HR dashboard', TRUE),
    ('55555555-5555-5555-5555-555555555137', 'DASHBOARD_DIRECTOR_VIEW', 'DASHBOARD', 'DIRECTOR_VIEW', 'GLOBAL', 'Open the director dashboard', TRUE),
    ('55555555-5555-5555-5555-555555555138', 'AUDIT_LOG_READ', 'AUDIT_LOG', 'READ', 'GLOBAL', 'Read audit logs', TRUE),
    ('55555555-5555-5555-5555-555555555139', 'ANALYTICS_READ', 'ANALYTICS', 'READ', 'SCOPED', 'Read analytics and reports', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO access_profiles (id, code, display_key, description_key, is_system_profile, is_active)
VALUES
    ('88888888-8888-8888-8888-888888888801', 'SELF_SERVICE', 'profile.selfService', 'profile.selfService.description', TRUE, TRUE),
    ('88888888-8888-8888-8888-888888888802', 'MANAGER_INBOX', 'profile.managerInbox', 'profile.managerInbox.description', TRUE, TRUE),
    ('88888888-8888-8888-8888-888888888803', 'HR_CONSOLE', 'profile.hrConsole', 'profile.hrConsole.description', TRUE, TRUE),
    ('88888888-8888-8888-8888-888888888804', 'ADMIN_CONSOLE', 'profile.adminConsole', 'profile.adminConsole.description', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET display_key = EXCLUDED.display_key,
    description_key = EXCLUDED.description_key,
    is_system_profile = EXCLUDED.is_system_profile,
    is_active = EXCLUDED.is_active;

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999901', 'menu.workspace.dashboard', 'menu.workspace.dashboard', 'WORKSPACE', '/dashboard', 'home', 10, TRUE),
    ('99999999-9999-9999-9999-999999999902', 'menu.workspace.newRequest', 'menu.workspace.newRequest', 'WORKSPACE', '/new-request', 'document-text', 20, TRUE),
    ('99999999-9999-9999-9999-999999999903', 'menu.workspace.myRequests', 'menu.workspace.myRequests', 'WORKSPACE', '/my-requests', 'calendar', 30, TRUE),
    ('99999999-9999-9999-9999-999999999904', 'menu.workspace.notifications', 'menu.workspace.notifications', 'WORKSPACE', '/notifications', 'inbox', 40, TRUE),
    ('99999999-9999-9999-9999-999999999905', 'menu.workspace.approvalInbox', 'menu.workspace.approvalInbox', 'WORKSPACE', '/approval-inbox', 'check-circle', 50, TRUE),
    ('99999999-9999-9999-9999-999999999906', 'menu.workspace.reports', 'menu.workspace.reports', 'WORKSPACE', '/reports', 'chart-bar', 60, TRUE),
    ('99999999-9999-9999-9999-999999999907', 'menu.administration.users', 'menu.administration.users', 'ADMINISTRATION', '/admin/users', 'users', 10, TRUE),
    ('99999999-9999-9999-9999-999999999908', 'menu.administration.accessProfiles', 'menu.administration.accessProfiles', 'ADMINISTRATION', '/admin/access-profiles', 'shield', 20, TRUE),
    ('99999999-9999-9999-9999-999999999909', 'menu.administration.teams', 'menu.administration.teams', 'ADMINISTRATION', '/admin/teams', 'users', 30, TRUE),
    ('99999999-9999-9999-9999-999999999910', 'menu.administration.teamHierarchy', 'menu.administration.teamHierarchy', 'ADMINISTRATION', '/admin/team-hierarchy', 'users', 40, TRUE),
    ('99999999-9999-9999-9999-999999999911', 'menu.administration.leaveBalances', 'menu.administration.leaveBalances', 'ADMINISTRATION', '/admin/leave-balances', 'calendar', 50, TRUE),
    ('99999999-9999-9999-9999-999999999912', 'menu.administration.adminRequests', 'menu.administration.adminRequests', 'ADMINISTRATION', '/admin/admin-requests', 'document-text', 60, TRUE),
    ('99999999-9999-9999-9999-999999999913', 'menu.administration.securityAudit', 'menu.administration.securityAudit', 'ADMINISTRATION', '/admin/security-audit', 'shield-check', 70, TRUE),
    ('99999999-9999-9999-9999-999999999914', 'menu.settings.quickSettings', 'menu.settings.quickSettings', 'SETTINGS', '/settings/quick', 'home', 10, TRUE),
    ('99999999-9999-9999-9999-999999999915', 'menu.settings.departments', 'menu.settings.departments', 'SETTINGS', '/settings/departments', 'building-office', 20, TRUE),
    ('99999999-9999-9999-9999-999999999916', 'menu.settings.projects', 'menu.settings.projects', 'SETTINGS', '/settings/projects', 'briefcase', 30, TRUE),
    ('99999999-9999-9999-9999-999999999917', 'menu.settings.hrCalendars', 'menu.settings.hrCalendars', 'SETTINGS', '/settings/hr-calendars', 'calendar', 40, TRUE),
    ('99999999-9999-9999-9999-999999999918', 'menu.settings.leaveTypesAcquisition', 'menu.settings.leaveTypesAcquisition', 'SETTINGS', '/settings/leave-types-acquisition', 'calendar', 50, TRUE),
    ('99999999-9999-9999-9999-999999999919', 'menu.settings.validationWorkflows', 'menu.settings.validationWorkflows', 'SETTINGS', '/settings/validation-workflows', 'check-circle', 60, TRUE)
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
JOIN menu_items menu ON menu.code IN (
    'menu.workspace.dashboard',
    'menu.workspace.newRequest',
    'menu.workspace.myRequests',
    'menu.workspace.notifications'
)
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code IN (
    'menu.workspace.dashboard',
    'menu.workspace.newRequest',
    'menu.workspace.myRequests',
    'menu.workspace.notifications',
    'menu.workspace.approvalInbox',
    'menu.workspace.reports'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON menu.code IN (
    'menu.workspace.dashboard',
    'menu.workspace.newRequest',
    'menu.workspace.myRequests',
    'menu.workspace.notifications',
    'menu.workspace.approvalInbox',
    'menu.workspace.reports',
    'menu.administration.teams',
    'menu.administration.teamHierarchy',
    'menu.administration.leaveBalances',
    'menu.administration.adminRequests',
    'menu.settings.quickSettings',
    'menu.settings.departments',
    'menu.settings.projects',
    'menu.settings.hrCalendars',
    'menu.settings.leaveTypesAcquisition',
    'menu.settings.validationWorkflows'
)
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT profile.id, menu.id, NOW(), NULL
FROM access_profiles profile
JOIN menu_items menu ON TRUE
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_READ',
    'LEAVE_BALANCE_READ'
)
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_READ',
    'LEAVE_BALANCE_READ',
    'APPROVAL_STEP_READ',
    'APPROVAL_STEP_DECIDE',
    'ANALYTICS_READ',
    'DASHBOARD_SUPERVISOR_VIEW'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_REQUEST_READ',
    'LEAVE_BALANCE_READ',
    'APPROVAL_STEP_READ',
    'APPROVAL_STEP_DECIDE',
    'ANALYTICS_READ',
    'DASHBOARD_HR_VIEW',
    'EMPLOYEE_READ',
    'EMPLOYEE_MANAGE',
    'DEPARTMENT_READ',
    'DEPARTMENT_MANAGE',
    'PROJECT_PORTFOLIO_MANAGE',
    'PROJECT_ASSIGNMENT_MANAGE',
    'TEAM_MANAGE',
    'LEAVE_TYPE_MANAGE',
    'ADMIN_REQUEST_INBOX_READ',
    'ADMIN_REQUEST_PROCESS',
    'ADMIN_REQUEST_APPROVE',
    'ADMIN_REQUEST_REJECT',
    'ADMIN_REQUEST_COMPLETE',
    'ADMIN_REQUEST_TYPE_MANAGE'
)
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON TRUE
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

COMMIT;
