ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS name VARCHAR(100);

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE permissions
SET name = UPPER(resource) || '_' || UPPER(action) || '_' || UPPER(scope)
WHERE name IS NULL;

ALTER TABLE permissions
    ALTER COLUMN name SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_permissions_name'
    ) THEN
        ALTER TABLE permissions
            ADD CONSTRAINT uq_permissions_name UNIQUE (name);
    END IF;
END $$;
