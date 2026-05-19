ALTER TABLE leave_balances
    ALTER COLUMN total_days TYPE NUMERIC(10,3) USING total_days::NUMERIC(10,3),
    ALTER COLUMN used_days TYPE NUMERIC(10,3) USING used_days::NUMERIC(10,3),
    ALTER COLUMN pending_days TYPE NUMERIC(10,3) USING pending_days::NUMERIC(10,3),
    ALTER COLUMN carry_over_days TYPE NUMERIC(10,3) USING carry_over_days::NUMERIC(10,3);

ALTER TABLE leave_balance_transactions
    ALTER COLUMN amount TYPE NUMERIC(10,3) USING amount::NUMERIC(10,3),
    ALTER COLUMN balance_after TYPE NUMERIC(10,3) USING balance_after::NUMERIC(10,3);

ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS duration_days NUMERIC(10,3),
    ADD COLUMN IF NOT EXISTS duration_hours NUMERIC(10,3),
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time TIME,
    ADD COLUMN IF NOT EXISTS partial_mode VARCHAR(50);

UPDATE leave_requests
SET duration_days = COALESCE(duration_days, working_days::NUMERIC(10,3)),
    partial_mode = COALESCE(partial_mode, CASE WHEN is_half_day THEN 'HALF_DAY' ELSE 'FULL_DAY' END)
WHERE duration_days IS NULL
   OR partial_mode IS NULL;

ALTER TABLE leave_requests
    ALTER COLUMN duration_days SET NOT NULL,
    ALTER COLUMN partial_mode SET NOT NULL;
