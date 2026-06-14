-- =============================================================================
-- V77: Compensation module Phase 1 — pay grades (per-tenant config) +
--      effective-dated employee compensation records (salary history)
-- =============================================================================
-- See docs/COMPENSATION_MODULE_DESIGN.md. Builds on the employee spine and the
-- org backbone (V72). Pay grades are per-tenant config cloned by provisioning
-- (like rating scales / job titles). Compensation records are effective-dated
-- with one current row per employee (partial unique index), same supersede
-- pattern as employee_contracts. Multi-tenant throughout: tenant_id DEFAULT from
-- the session setting, FORCE row-level security, grants to hris_app.

-- Reusable RLS helper expression (matches V66/V72/V73):
--   USING/WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)

-- -----------------------------------------------------------------------------
-- 1. Pay grades / salary bands (per-tenant config; cloned by provisioning)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_pay_grades (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    currency      VARCHAR(3) NOT NULL DEFAULT 'USD',
    pay_frequency VARCHAR(20) NOT NULL DEFAULT 'ANNUAL',  -- ANNUAL | MONTHLY | HOURLY
    min_amount    NUMERIC(14,2) NOT NULL,
    mid_amount    NUMERIC(14,2) NOT NULL,
    max_amount    NUMERIC(14,2) NOT NULL,
    job_family    VARCHAR(100),  -- optional advisory match to job_titles.family (not an FK)
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_pay_grades_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_pay_grades_band CHECK (min_amount <= mid_amount AND mid_amount <= max_amount)
);

ALTER TABLE compensation_pay_grades ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_pay_grades FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_pay_grades
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_pay_grades TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Compensation records (effective-dated per-employee salary history)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_records (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id   UUID NOT NULL REFERENCES employees(id),
    pay_grade_id  UUID REFERENCES compensation_pay_grades(id),
    base_amount   NUMERIC(14,2) NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    pay_frequency VARCHAR(20) NOT NULL,  -- ANNUAL | MONTHLY | HOURLY
    effective_date DATE NOT NULL,
    end_date      DATE,
    is_current    BOOLEAN NOT NULL DEFAULT TRUE,
    change_reason VARCHAR(30) NOT NULL,  -- HIRE | MERIT | PROMOTION | MARKET_ADJUSTMENT | DEMOTION | CORRECTION | OTHER
    compa_ratio   NUMERIC(6,4),
    note          TEXT,
    created_by    UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

-- One current compensation record per employee per tenant.
CREATE UNIQUE INDEX uq_comp_records_current
    ON compensation_records (employee_id, tenant_id) WHERE is_current;
CREATE INDEX idx_comp_records_employee ON compensation_records (employee_id);

ALTER TABLE compensation_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_records
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_records TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Permissions (global catalog) + per-tenant profile grants
-- -----------------------------------------------------------------------------

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    (gen_random_uuid(), 'COMPENSATION_VIEW_OWN', 'COMPENSATION', 'VIEW_OWN',
        'SELF', 'View own compensation (current pay and history)', TRUE),
    (gen_random_uuid(), 'COMPENSATION_MANAGE', 'COMPENSATION', 'MANAGE',
        'GLOBAL', 'Manage pay grades and employee compensation records', TRUE)
ON CONFLICT (name) DO NOTHING;

-- COMPENSATION_VIEW_OWN to every profile (own self-view); MANAGE to HR/admin.
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'COMPENSATION_VIEW_OWN'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'COMPENSATION_MANAGE'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Menu items (global catalog) + per-tenant grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999951', 'menu.workspace.myCompensation',
        'menu.workspace.myCompensation', 'WORKSPACE', '/compensation', 'wallet', 45, TRUE),
    ('99999999-9999-9999-9999-999999999952', 'menu.configuration.payGrades',
        'menu.configuration.payGrades', 'CONFIGURATION', '/settings/pay-grades', 'layers', 46, TRUE)
ON CONFLICT (code) DO NOTHING;

-- My Compensation to every profile; Pay Grades to HR/admin.
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.myCompensation'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.configuration.payGrades'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Seed a starter pay-grade ladder for every existing tenant
-- -----------------------------------------------------------------------------
-- Flyway runs as hris_user (no RLS context), so tenant_id is set explicitly.

INSERT INTO compensation_pay_grades
    (id, code, name, currency, pay_frequency, min_amount, mid_amount, max_amount, is_active, tenant_id)
SELECT gen_random_uuid(), g.code, g.name, 'USD', 'ANNUAL',
       g.min_amount, g.mid_amount, g.max_amount, TRUE, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('G1', 'Grade 1 — Entry',        40000, 50000, 60000),
    ('G2', 'Grade 2 — Associate',    55000, 70000, 85000),
    ('G3', 'Grade 3 — Professional', 75000, 95000, 115000),
    ('G4', 'Grade 4 — Senior',       100000, 125000, 150000),
    ('G5', 'Grade 5 — Lead',         135000, 165000, 195000)
) AS g(code, name, min_amount, mid_amount, max_amount);
