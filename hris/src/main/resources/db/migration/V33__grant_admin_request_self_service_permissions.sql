INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN (
      'ADMIN_REQUEST_CREATE',
      'ADMIN_REQUEST_READ_OWN',
      'ADMIN_REQUEST_CANCEL_OWN'
  )
WHERE profile.is_active = TRUE
ON CONFLICT (profile_id, permission_id) DO NOTHING;
