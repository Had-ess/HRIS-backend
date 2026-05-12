-- Phase 7: Event standardization, scheduler controls, audit consistency
BEGIN;

-- 1. Add correlationId to notification_events
ALTER TABLE notification_events ADD COLUMN IF NOT EXISTS correlation_id UUID;

-- 2. Add eventId to notifications for idempotency
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_event_id ON notifications(event_id) WHERE event_id IS NOT NULL;

-- 3. Add actorType to audit_logs
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_type VARCHAR(20) DEFAULT 'USER';

-- 4. Leave accrual run table
CREATE TABLE IF NOT EXISTS leave_accrual_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date DATE NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    policies_processed INT NOT NULL DEFAULT 0,
    employees_processed INT NOT NULL DEFAULT 0,
    transactions_created INT NOT NULL DEFAULT 0,
    error_message TEXT,
    triggered_by VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    triggered_by_user_id UUID,
    CONSTRAINT fk_accrual_run_user FOREIGN KEY (triggered_by_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_accrual_runs_date ON leave_accrual_runs(run_date);
CREATE INDEX IF NOT EXISTS idx_accrual_runs_status ON leave_accrual_runs(status);

-- 5. SLA notification marker on admin_requests
ALTER TABLE admin_requests ADD COLUMN IF NOT EXISTS sla_notified_at TIMESTAMPTZ;

-- 6. New permissions for accrual run management
INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
  (gen_random_uuid(), 'ACCRUAL_RUN_READ', 'ACCRUAL_RUN', 'READ', 'GLOBAL', 'View accrual run history', TRUE),
  (gen_random_uuid(), 'ACCRUAL_RUN_MANAGE', 'ACCRUAL_RUN', 'MANAGE', 'GLOBAL', 'Trigger manual accrual runs', TRUE)
ON CONFLICT (name) DO NOTHING;

-- 7. Assign accrual permissions to ADMIN_CONSOLE and HR_CONSOLE profiles
INSERT INTO profile_permissions (id, profile_id, permission_id, granted_at, granted_by_id)
SELECT gen_random_uuid(), ap.id, p.id, NOW(), NULL
FROM access_profiles ap, permissions p
WHERE ap.code IN ('ADMIN_CONSOLE', 'HR_CONSOLE')
  AND p.name IN ('ACCRUAL_RUN_READ', 'ACCRUAL_RUN_MANAGE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- 8. Add menu item for accrual runs under SETTINGS section (near Leave Types & Acquisition)
INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES (gen_random_uuid(), 'menu.settings.accrualRuns', 'menu.settings.accrualRuns', 'SETTINGS',
        '/settings/accrual-runs', 'clock', 55, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 9. Assign accrual runs menu access to ADMIN_CONSOLE and HR_CONSOLE
INSERT INTO profile_menu_access (id, profile_id, menu_item_id, granted_at, granted_by_id)
SELECT gen_random_uuid(), ap.id, mi.id, NOW(), NULL
FROM access_profiles ap, menu_items mi
WHERE ap.code IN ('ADMIN_CONSOLE', 'HR_CONSOLE')
  AND mi.code = 'menu.settings.accrualRuns'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

COMMIT;
