ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_leave_requests_cancelled_at ON leave_requests(cancelled_at);
CREATE INDEX IF NOT EXISTS idx_leave_requests_deleted_at ON leave_requests(deleted_at);
