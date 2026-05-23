-- Drop the unused @Version columns from approval_steps and approval_workflows.
-- Concurrent decides are serialised by pessimistic SELECT ... FOR UPDATE in
-- ApprovalStepService (findByIdForUpdate), so the optimistic version column
-- was never consulted at runtime.

ALTER TABLE approval_steps     DROP COLUMN IF EXISTS version;
ALTER TABLE approval_workflows DROP COLUMN IF EXISTS version;
