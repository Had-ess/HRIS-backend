-- Drop the unused @Version column from leave_balances. The application code
-- serialises concurrent updates via pessimistic SELECT FOR UPDATE in
-- LeaveBalanceLedgerService.getOrCreateBalanceForUpdate, so the optimistic
-- version column was never consulted at runtime.

ALTER TABLE leave_balances DROP COLUMN IF EXISTS version;
