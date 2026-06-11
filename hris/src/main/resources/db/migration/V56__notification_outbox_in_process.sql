-- =============================================================================
-- V56: In-process notification outbox (RabbitMQ removal)
-- =============================================================================
-- The notification_events outbox table becomes the queue itself; the broker is
-- gone. Failed processing is retried by the outbox worker; after max attempts
-- the row is stamped failed_at (dead-letter equivalent) and excluded from
-- retry scans.
-- =============================================================================

ALTER TABLE notification_events
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN failed_at TIMESTAMPTZ;

COMMENT ON COLUMN notification_events.attempts IS
    'Number of failed processing attempts by the outbox worker';
COMMENT ON COLUMN notification_events.failed_at IS
    'Set when processing exhausted max attempts; row is dead-lettered and no longer retried';

-- Retry scans only ever touch unprocessed, non-failed rows.
CREATE INDEX idx_notification_events_pending
    ON notification_events (published_at)
    WHERE delivered_at IS NULL AND failed_at IS NULL;
