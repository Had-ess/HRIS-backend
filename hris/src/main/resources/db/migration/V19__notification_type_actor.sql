-- Add notification category type and actor display name for UI filtering and avatar display
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS type               VARCHAR(20)  NULL,
    ADD COLUMN IF NOT EXISTS actor_display_name VARCHAR(255) NULL;

-- Back-fill existing rows: classify by linkPath heuristic
UPDATE notifications SET type = 'LEAVE'   WHERE type IS NULL AND link_path ILIKE '%/leave%';
UPDATE notifications SET type = 'REQUEST' WHERE type IS NULL AND link_path ILIKE '%/request%';
UPDATE notifications SET type = 'SYSTEM'  WHERE type IS NULL;
