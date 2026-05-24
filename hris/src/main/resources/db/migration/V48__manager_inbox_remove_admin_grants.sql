-- Department managers (MANAGER_INBOX) should only READ employees, departments,
-- and projects within their scope. Creating/deleting employees and departments
-- and managing the global project portfolio are HR/admin responsibilities.
--
-- V46/V47 over-granted these perms; this migration revokes them so the UI's
-- permission-based gating (e.g. `canCreate()` = hasPermissionName('EMPLOYEE_MANAGE'))
-- hides the action buttons that the backend would reject anyway.

DELETE FROM profile_permissions pp
USING access_profiles profile, permissions permission
WHERE pp.profile_id = profile.id
  AND pp.permission_id = permission.id
  AND profile.code = 'MANAGER_INBOX'
  AND permission.name IN (
      'EMPLOYEE_MANAGE',
      'DEPARTMENT_MANAGE',
      'PROJECT_PORTFOLIO_MANAGE'
  );
