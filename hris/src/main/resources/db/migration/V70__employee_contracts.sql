-- ============================================================================
-- V70: Employee lifecycle — contract records (see docs/EMPLOYEE_LIFECYCLE_DESIGN.md)
--
-- Versioned contracts per employee: exactly one ACTIVE row per employee
-- (partial unique index); creating a new contract supersedes the previous one.
-- No new permissions or menu items — lifecycle UI lives inside the existing
-- employee pages behind EMPLOYEE_READ / EMPLOYEE_MANAGE.
-- ============================================================================

CREATE TABLE employee_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    contract_type VARCHAR(50) NOT NULL
        CHECK (contract_type IN ('PERMANENT', 'FIXED_TERM', 'INTERNSHIP', 'CONTRACTOR')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'EXPIRED', 'TERMINATED')),
    start_date DATE NOT NULL,
    end_date DATE,
    probation_end_date DATE,
    note VARCHAR(500),
    expiry_notified_at TIMESTAMPTZ,
    probation_notified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT ck_contract_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uq_employee_contracts_active
    ON employee_contracts (tenant_id, employee_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_employee_contracts_employee ON employee_contracts (employee_id);
CREATE INDEX idx_employee_contracts_end ON employee_contracts (end_date) WHERE status = 'ACTIVE';

ALTER TABLE employee_contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_contracts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON employee_contracts
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON employee_contracts TO hris_app;

-- ----------------------------------------------------------------------------
-- Backfill: one contract per existing employee, derived from current columns.
-- Terminated employees get a TERMINATED contract ending on termination_date;
-- everyone else gets an ACTIVE contract starting at hire_date. Explicit
-- e.tenant_id because Flyway runs without a tenant session setting.
-- ----------------------------------------------------------------------------
INSERT INTO employee_contracts
    (employee_id, contract_type, status, start_date, end_date, note, tenant_id)
SELECT
    e.id,
    e.contract_type,
    CASE WHEN e.status = 'TERMINATED' THEN 'TERMINATED' ELSE 'ACTIVE' END,
    e.hire_date,
    CASE WHEN e.status = 'TERMINATED'
         THEN GREATEST(COALESCE(e.termination_date, e.hire_date), e.hire_date)
         ELSE NULL END,
    'Backfilled from employee record (V70)',
    e.tenant_id
FROM employees e;
