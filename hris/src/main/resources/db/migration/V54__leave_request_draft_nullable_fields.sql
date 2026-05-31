ALTER TABLE leave_requests
    ALTER COLUMN leave_type_id DROP NOT NULL,
    ALTER COLUMN start_date DROP NOT NULL,
    ALTER COLUMN end_date DROP NOT NULL,
    ALTER COLUMN urgency_level DROP NOT NULL;

