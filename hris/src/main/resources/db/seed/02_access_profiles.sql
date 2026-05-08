-- Optional manual seed.
-- The Flyway baseline already inserts the current access profile model.

BEGIN;

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

COMMIT;
