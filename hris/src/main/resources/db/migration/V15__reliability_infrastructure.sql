-- =========================================================================
-- V15: Reliability infrastructure
-- Adds: ShedLock table (H2), is_seed flag on users (H4b),
--       delivered_at on notification_events (H3)
-- =========================================================================

-- H2: ShedLock distributed lock table
-- Used by @SchedulerLock to ensure only one instance runs each cron job.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- H4b: Seed-account flag on users
-- Replaces the KC_REPLACE_* / KC_DEMO_* keycloak_id prefix convention.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_seed BOOLEAN NOT NULL DEFAULT FALSE;

-- Back-fill: existing placeholder rows identified by their prefix.
-- After Task 4 this column is the authoritative signal; the prefix
-- convention is only kept for backward-compat identification here.
UPDATE users
SET    is_seed = TRUE
WHERE  keycloak_id LIKE 'KC_REPLACE_%'
   OR  keycloak_id LIKE 'KC_DEMO_%';

-- H3: Delivery tracking on notification events
-- NULL means undelivered; non-NULL is the timestamp of confirmed delivery.
ALTER TABLE notification_events
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

-- Partial index for the outbox worker's undelivered-events query.
CREATE INDEX IF NOT EXISTS idx_notification_events_undelivered
    ON notification_events (published_at)
    WHERE delivered_at IS NULL;
