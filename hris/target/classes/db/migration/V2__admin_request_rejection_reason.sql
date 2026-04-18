ALTER TABLE admin_requests
ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
