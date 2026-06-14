-- =============================================================================
-- V79: Compensation module Phase 3 — variable / bonus pay
-- =============================================================================
-- See docs/COMPENSATION_MODULE_DESIGN.md (section 5). Adds the variable-pay half:
-- per-tenant bonus plans (target % of base), bonus cycles that mirror the merit
-- cycle (DRAFT -> ACTIVE -> IN_REVIEW -> CLOSED) with per-department pools and a
-- manager worksheet, full STI payout (target% x annualized base x performance
-- factor x company funding factor), and ad-hoc SPOT awards. Bonus is one-time and
-- append-only: awards live in compensation_bonus_awards and NEVER touch
-- compensation_records. Multi-tenant throughout: tenant_id DEFAULT from session,
-- FORCE RLS, grants to hris_app. Only bonus plans are per-tenant config
-- (TEMPLATE_TABLES + seeded); cycles / pools / awards are transactional.

-- -----------------------------------------------------------------------------
-- 1. Bonus plans (per-tenant config; cloned by provisioning; seeded)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_bonus_plans (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(150) NOT NULL,
    target_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id      UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_bonus_plan_code UNIQUE (tenant_id, code)
);

ALTER TABLE compensation_bonus_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_bonus_plans FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_bonus_plans
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_bonus_plans TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Bonus cycles (transactional)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_bonus_cycles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                        VARCHAR(200) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | IN_REVIEW | CLOSED
    bonus_plan_id               UUID NOT NULL REFERENCES compensation_bonus_plans(id),
    source_performance_cycle_id UUID,  -- perf cycle whose facts seed ratings (nullable)
    payout_date                 DATE NOT NULL,
    company_funding_factor      NUMERIC(6,4) NOT NULL DEFAULT 1.0000,  -- scales the whole cycle (0.8 = 80% funded)
    rating_low_max              INT NOT NULL DEFAULT 2,   -- rating <= this -> LOW band
    rating_high_min             INT NOT NULL DEFAULT 4,   -- rating >= this -> HIGH band
    perf_factor_low             NUMERIC(6,4) NOT NULL DEFAULT 0.5000,
    perf_factor_solid           NUMERIC(6,4) NOT NULL DEFAULT 1.0000,
    perf_factor_high            NUMERIC(6,4) NOT NULL DEFAULT 1.2500,
    include_sub_departments     BOOLEAN NOT NULL DEFAULT FALSE,
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

ALTER TABLE compensation_bonus_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_bonus_cycles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_bonus_cycles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_bonus_cycles TO hris_app;

-- Department scope for a cycle (empty = all departments)
CREATE TABLE compensation_bonus_cycle_departments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id      UUID NOT NULL REFERENCES compensation_bonus_cycles(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_bonus_cycle_dept UNIQUE (cycle_id, department_id)
);

ALTER TABLE compensation_bonus_cycle_departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_bonus_cycle_departments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_bonus_cycle_departments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_bonus_cycle_departments TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Per-department bonus pools (transactional; generated on activate)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_bonus_pools (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id      UUID NOT NULL REFERENCES compensation_bonus_cycles(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id),
    base_payroll  NUMERIC(16,2) NOT NULL DEFAULT 0,  -- snapshot sum of annualized current base
    target_amount NUMERIC(16,2) NOT NULL DEFAULT 0,  -- fully-funded sum of computed awards
    budget_amount NUMERIC(16,2) NOT NULL DEFAULT 0,  -- HR-editable hard cap; defaulted to target_amount
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_bonus_pool_cycle_dept UNIQUE (cycle_id, department_id)
);

CREATE INDEX idx_bonus_pools_cycle ON compensation_bonus_pools (cycle_id);

ALTER TABLE compensation_bonus_pools ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_bonus_pools FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_bonus_pools
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_bonus_pools TO hris_app;

-- -----------------------------------------------------------------------------
-- 4. Bonus awards (transactional; append-only; cycle awards + ad-hoc SPOT awards)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_bonus_awards (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id                 UUID REFERENCES compensation_bonus_cycles(id) ON DELETE CASCADE,  -- NULL for SPOT
    employee_id              UUID NOT NULL REFERENCES employees(id),
    department_id            UUID,
    manager_employee_id      UUID,  -- resolved via reviewer-resolution (spine -> dept head)
    bonus_plan_id            UUID REFERENCES compensation_bonus_plans(id),
    award_type               VARCHAR(20) NOT NULL DEFAULT 'CYCLE',  -- CYCLE | SPOT
    current_base_amount      NUMERIC(14,2) NOT NULL,
    currency                 VARCHAR(3) NOT NULL,
    pay_frequency            VARCHAR(20) NOT NULL,  -- ANNUAL | MONTHLY | HOURLY
    target_percent           NUMERIC(5,2) NOT NULL DEFAULT 0,
    performance_rating_value INT,
    potential_rating_value   INT,
    rating_band              VARCHAR(10),  -- LOW | SOLID | HIGH
    performance_factor       NUMERIC(6,4) NOT NULL DEFAULT 1.0000,
    company_factor           NUMERIC(6,4) NOT NULL DEFAULT 1.0000,
    suggested_amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    awarded_amount           NUMERIC(14,2),
    payout_date              DATE,
    status                   VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROPOSED | APPROVED | REJECTED | PAID
    note                     TEXT,
    proposed_by              UUID REFERENCES users(id),
    approved_by              UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    -- One cycle award per employee; NULL cycle_id (SPOT) rows are distinct, so an
    -- employee can hold many spot awards.
    CONSTRAINT uq_bonus_award_cycle_employee UNIQUE (cycle_id, employee_id)
);

CREATE INDEX idx_bonus_awards_cycle ON compensation_bonus_awards (cycle_id);
CREATE INDEX idx_bonus_awards_cycle_manager ON compensation_bonus_awards (cycle_id, manager_employee_id);
CREATE INDEX idx_bonus_awards_employee ON compensation_bonus_awards (employee_id);

ALTER TABLE compensation_bonus_awards ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_bonus_awards FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_bonus_awards
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_bonus_awards TO hris_app;

-- -----------------------------------------------------------------------------
-- 5. Menu items (global catalog) + per-tenant grants
-- -----------------------------------------------------------------------------
-- No new permission: the manager bonus worksheet reuses COMPENSATION_REVIEW
-- (data-scoped to reports); HR reuses COMPENSATION_MANAGE.

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999956', 'menu.workspace.bonusReview',
        'menu.workspace.bonusReview', 'WORKSPACE', '/compensation/bonus', 'gift', 50, TRUE),
    ('99999999-9999-9999-9999-999999999957', 'menu.configuration.bonusCycles',
        'menu.configuration.bonusCycles', 'CONFIGURATION', '/settings/bonus-cycles', 'gift', 51, TRUE),
    ('99999999-9999-9999-9999-999999999958', 'menu.configuration.bonusPlans',
        'menu.configuration.bonusPlans', 'CONFIGURATION', '/settings/bonus-plans', 'layers', 52, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Manager worksheet menu to every profile (data-scoped); admin menus to HR/admin.
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.bonusReview'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code IN ('menu.configuration.bonusCycles', 'menu.configuration.bonusPlans')
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Seed a starter bonus plan for every existing tenant
-- -----------------------------------------------------------------------------
-- Flyway runs as hris_user (no RLS context), so tenant_id is set explicitly.

INSERT INTO compensation_bonus_plans (id, code, name, target_percent, is_active, tenant_id)
SELECT gen_random_uuid(), 'ANNUAL_STI', 'Annual Incentive Plan', 10.00, TRUE, t.id
FROM tenants t;
