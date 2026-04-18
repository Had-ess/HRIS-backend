ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS name VARCHAR(255);

ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

WITH generated_names AS (
    SELECT
        id,
        CASE
            WHEN COUNT(*) OVER (PARTITION BY resource, action) > 1
                THEN UPPER(resource) || '_' || UPPER(action) || '_' || UPPER(scope)
            ELSE UPPER(resource) || '_' || UPPER(action)
        END AS generated_name
    FROM permissions
)
UPDATE permissions p
SET name = generated_names.generated_name
FROM generated_names
WHERE p.id = generated_names.id
  AND (p.name IS NULL OR BTRIM(p.name) = '');

ALTER TABLE permissions
ALTER COLUMN name SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_permissions_name
    ON permissions(name);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_permissions_resource_action_scope'
          AND conrelid = 'permissions'::regclass
    ) THEN
        ALTER TABLE permissions
            ADD CONSTRAINT uq_permissions_resource_action_scope
            UNIQUE (resource, action, scope);
    END IF;
END $$;
