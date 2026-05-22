-- Make the Administration Requests menu item visible to any active access
-- profile that has at least one admin-request related permission. This covers
-- self-service profiles that only hold ADMIN_REQUEST_READ_OWN / CANCEL_OWN /
-- CREATE, which previously could not see or open the page even though the
-- backend already exposed the /api/admin-requests/my endpoints for them.

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id)
SELECT DISTINCT profile.id,
       menu.id,
       NOW(),
       NULL::uuid
FROM access_profiles profile
JOIN profile_permissions pp ON pp.profile_id = profile.id
JOIN permissions permission ON permission.id = pp.permission_id
JOIN menu_items menu ON menu.code = 'menu.administration.adminRequests'
WHERE profile.is_active = TRUE
  AND permission.name IN (
      'ADMIN_REQUEST_READ_OWN',
      'ADMIN_REQUEST_CANCEL_OWN',
      'ADMIN_REQUEST_CREATE',
      'ADMIN_REQUEST_INBOX_READ',
      'ADMIN_REQUEST_READ_GLOBAL',
      'ADMIN_REQUEST_PROCESS',
      'ADMIN_REQUEST_APPROVE',
      'ADMIN_REQUEST_REJECT',
      'ADMIN_REQUEST_COMPLETE',
      'ADMIN_REQUEST_TYPE_MANAGE'
  )
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
