INSERT INTO roles (id, code, name, is_system_role, is_active, parent_id)
SELECT gen_random_uuid(), 'ADMINISTRATION', 'Administration', TRUE, TRUE, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'ADMINISTRATION'
);

UPDATE roles hr
SET parent_id = admin.id
FROM roles admin
WHERE hr.code = 'HR_ADMIN'
  AND admin.code = 'ADMINISTRATION'
  AND hr.parent_id IS DISTINCT FROM admin.id;

INSERT INTO role_permissions (id, role_id, permission_id, granted_at, granted_by_id)
SELECT gen_random_uuid(), admin.id, rp.permission_id, NOW(), rp.granted_by_id
FROM roles admin
JOIN roles hr ON hr.code = 'HR_ADMIN'
JOIN role_permissions rp ON rp.role_id = hr.id
WHERE admin.code = 'ADMINISTRATION'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = admin.id
        AND existing.permission_id = rp.permission_id
  );

DELETE FROM role_permissions rp
USING roles hr, permissions p
WHERE rp.role_id = hr.id
  AND hr.code = 'HR_ADMIN'
  AND rp.permission_id = p.id
  AND (
      (UPPER(p.resource) = 'USER' AND UPPER(p.action) <> 'READ')
      OR UPPER(p.resource) IN ('ROLE', 'PERMISSION')
      OR (UPPER(p.resource) = 'DEPARTMENT' AND UPPER(p.action) IN ('CREATE', 'UPDATE', 'DELETE', 'DEACTIVATE'))
      OR (UPPER(p.resource) = 'PROJECT' AND UPPER(p.action) IN ('CREATE', 'UPDATE', 'DELETE', 'ASSIGN_DEPARTMENT', 'REMOVE_DEPARTMENT', 'ASSIGN_EMPLOYEE', 'REMOVE_EMPLOYEE'))
  );
