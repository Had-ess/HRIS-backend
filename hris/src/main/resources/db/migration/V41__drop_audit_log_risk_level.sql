-- Drop the unused audit_logs.risk_level column. The field was declared on the
-- AuditLog entity but never populated by AuditLogService.log(...), and the
-- "filter by risk level" UI was never wired against a backing service.
-- Risk grading is reserved for a later iteration.

ALTER TABLE audit_logs DROP COLUMN IF EXISTS risk_level;
