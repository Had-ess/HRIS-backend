-- V17: Add location to employees, risk_level to audit_logs, openings to departments

ALTER TABLE employees ADD COLUMN location VARCHAR(100);

ALTER TABLE audit_logs ADD COLUMN risk_level VARCHAR(20);

ALTER TABLE departments ADD COLUMN openings INTEGER NOT NULL DEFAULT 0;
