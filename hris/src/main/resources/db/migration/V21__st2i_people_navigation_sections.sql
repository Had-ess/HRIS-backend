-- Align backend-driven navigation with the ST2I People sidebar taxonomy.
-- Existing menu IDs and profile_menu_access grants are preserved.

UPDATE menu_items
SET section_code = 'WORKSPACE',
    translation_key = 'menu.workspace.dashboard',
    display_order = 10,
    route = '/dashboard',
    icon = 'home'
WHERE code = 'menu.workspace.dashboard';

UPDATE menu_items
SET section_code = 'WORKSPACE',
    translation_key = 'menu.workspace.notifications',
    display_order = 20,
    route = '/notifications',
    icon = 'inbox'
WHERE code = 'menu.workspace.notifications';

UPDATE menu_items
SET section_code = 'TIME_OFF',
    translation_key = 'menu.timeOff.leaveRequests',
    display_order = 10,
    route = '/leave',
    icon = 'calendar'
WHERE code = 'menu.workspace.myRequests';

UPDATE menu_items
SET section_code = 'TIME_OFF',
    translation_key = 'menu.timeOff.myBalances',
    display_order = 20,
    route = '/admin/leave-balances',
    icon = 'calendar'
WHERE code = 'menu.administration.leaveBalances';

UPDATE menu_items
SET section_code = 'TIME_OFF',
    translation_key = 'menu.timeOff.approvals',
    display_order = 30,
    route = '/approval-inbox',
    icon = 'check-circle'
WHERE code = 'menu.workspace.approvalInbox';

UPDATE menu_items
SET section_code = 'REQUESTS',
    translation_key = 'menu.requests.adminRequests',
    display_order = 10,
    route = '/admin/admin-requests',
    icon = 'document-text'
WHERE code = 'menu.administration.adminRequests';

UPDATE menu_items
SET section_code = 'PEOPLE',
    translation_key = 'menu.people.employees',
    display_order = 10,
    route = '/employees',
    icon = 'users'
WHERE code = 'menu.administration.employees';

UPDATE menu_items
SET section_code = 'PEOPLE',
    translation_key = 'menu.people.departments',
    display_order = 20,
    route = '/settings/departments',
    icon = 'building-office'
WHERE code = 'menu.settings.departments';

UPDATE menu_items
SET section_code = 'PEOPLE',
    translation_key = 'menu.people.projects',
    display_order = 30,
    route = '/settings/projects',
    icon = 'briefcase'
WHERE code = 'menu.settings.projects';

UPDATE menu_items
SET section_code = 'PEOPLE',
    translation_key = 'menu.people.teamHierarchy',
    display_order = 40,
    route = '/admin/team-hierarchy',
    icon = 'users'
WHERE code = 'menu.administration.teamHierarchy';

UPDATE menu_items
SET section_code = 'INSIGHTS',
    translation_key = 'menu.insights.analytics',
    display_order = 10,
    route = '/reports',
    icon = 'chart-bar'
WHERE code = 'menu.workspace.reports';

UPDATE menu_items
SET section_code = 'INSIGHTS',
    translation_key = 'menu.insights.auditLog',
    display_order = 20,
    route = '/admin/security-audit',
    icon = 'shield-check'
WHERE code = 'menu.administration.securityAudit';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.leaveTypes',
    display_order = 10,
    route = '/settings/leave-types-acquisition',
    icon = 'calendar'
WHERE code = 'menu.settings.leaveTypesAcquisition';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.validationFlows',
    display_order = 20,
    route = '/settings/validation-workflows',
    icon = 'check-circle'
WHERE code = 'menu.settings.validationWorkflows';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.hrCalendar',
    display_order = 30,
    route = '/settings/hr-calendars',
    icon = 'calendar'
WHERE code = 'menu.settings.hrCalendars';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.accessProfiles',
    display_order = 40,
    route = '/admin/access-profiles',
    icon = 'shield'
WHERE code = 'menu.administration.accessProfiles';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.accrualRuns',
    display_order = 50,
    route = '/settings/accrual-runs',
    icon = 'calendar'
WHERE code = 'menu.settings.accrualRuns';

UPDATE menu_items
SET section_code = 'CONFIGURATION',
    translation_key = 'menu.configuration.quickSettings',
    display_order = 60,
    route = '/settings/quick',
    icon = 'home'
WHERE code = 'menu.settings.quickSettings';
