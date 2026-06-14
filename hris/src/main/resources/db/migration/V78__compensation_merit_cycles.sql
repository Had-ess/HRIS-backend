-- =============================================================================
-- V78: Compensation module Phase 2 — merit / compensation-review cycles
-- =============================================================================
-- See docs/COMPENSATION_MODULE_DESIGN.md (section 4). Closes the
-- perform -> calibrate -> reward loop: a comp-review cycle reads each in-scope
-- employee's performance result (analytics_performance_facts) + current
-- compa-ratio, runs them through a per-tenant merit matrix (rating band x
-- compa-ratio band -> suggested %), lets managers propose increases inside a
-- per-department budget pool, routes through one HR approval gate, and on apply
-- writes new compensation_records (reason MERIT/PROMOTION) via the Phase-1
-- supersede. Multi-tenant throughout: tenant_id DEFAULT from session, FORCE RLS,
-- grants to hris_app. Only the merit matrix is per-tenant config (TEMPLATE_TABLES
-- + seeded); cycles / pools / proposals are transactional.

-- -----------------------------------------------------------------------------
-- 1. Merit matrix (per-tenant config; cloned by provisioning; seeded)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_merit_matrix (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating_band      VARCHAR(10) NOT NULL,  -- LOW | SOLID | HIGH
    compa_band       VARCHAR(10) NOT NULL,  -- BELOW | WITHIN | ABOVE
    suggested_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id        UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_merit_matrix_cell UNIQUE (tenant_id, rating_band, compa_band)
);

ALTER TABLE compensation_merit_matrix ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_merit_matrix FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_merit_matrix
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_merit_matrix TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Comp-review cycles (transactional)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_review_cycles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                        VARCHAR(200) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | IN_REVIEW | CLOSED
    source_performance_cycle_id UUID,  -- perf cycle whose facts seed ratings (nullable)
    effective_date              DATE NOT NULL,
    default_budget_percent      NUMERIC(5,2) NOT NULL DEFAULT 3.00,
    rating_low_max              INT NOT NULL DEFAULT 2,   -- rating <= this -> LOW band
    rating_high_min             INT NOT NULL DEFAULT 4,   -- rating >= this -> HIGH band
    compa_low_max               NUMERIC(6,4) NOT NULL DEFAULT 0.9000,  -- compa < this -> BELOW band
    compa_high_min              NUMERIC(6,4) NOT NULL DEFAULT 1.1000,  -- compa > this -> ABOVE band
    include_sub_departments     BOOLEAN NOT NULL DEFAULT FALSE,
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

ALTER TABLE compensation_review_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_review_cycles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_review_cycles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_review_cycles TO hris_app;

-- Department scope for a cycle (empty = all departments)
CREATE TABLE compensation_review_cycle_departments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id      UUID NOT NULL REFERENCES compensation_review_cycles(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_comp_cycle_dept UNIQUE (cycle_id, department_id)
);

ALTER TABLE compensation_review_cycle_departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_review_cycle_departments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_review_cycle_departments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_review_cycle_departments TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Per-department budget pools (transactional; generated on activate)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_budget_pools (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id      UUID NOT NULL REFERENCES compensation_review_cycles(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id),
    base_payroll  NUMERIC(16,2) NOT NULL DEFAULT 0,  -- snapshot sum of annualized current base
    budget_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    budget_amount NUMERIC(16,2) NOT NULL DEFAULT 0,  -- HR-editable; defaulted base_payroll * pct
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_budget_pool_cycle_dept UNIQUE (cycle_id, department_id)
);

CREATE INDEX idx_budget_pools_cycle ON compensation_budget_pools (cycle_id);

ALTER TABLE compensation_budget_pools ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_budget_pools FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_budget_pools
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_budget_pools TO hris_app;

-- -----------------------------------------------------------------------------
-- 4. Proposals (transactional; one per cycle x employee, generated on activate)
-- -----------------------------------------------------------------------------

CREATE TABLE compensation_proposals (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id                 UUID NOT NULL REFERENCES compensation_review_cycles(id) ON DELETE CASCADE,
    employee_id              UUID NOT NULL REFERENCES employees(id),
    department_id            UUID,
    manager_employee_id      UUID,  -- resolved via reviewer-resolution (spine -> dept head)
    pay_grade_id             UUID REFERENCES compensation_pay_grades(id),
    current_base_amount      NUMERIC(14,2) NOT NULL,
    currency                 VARCHAR(3) NOT NULL,
    pay_frequency            VARCHAR(20) NOT NULL,  -- ANNUAL | MONTHLY | HOURLY
    current_compa_ratio      NUMERIC(6,4),
    performance_rating_value INT,
    potential_rating_value   INT,
    rating_band              VARCHAR(10) NOT NULL,  -- LOW | SOLID | HIGH
    compa_band               VARCHAR(10) NOT NULL,  -- BELOW | WITHIN | ABOVE
    suggested_percent        NUMERIC(5,2) NOT NULL DEFAULT 0,
    proposed_percent         NUMERIC(5,2),
    proposed_increase_amount NUMERIC(14,2),
    proposed_base_amount     NUMERIC(14,2),
    change_reason            VARCHAR(30) NOT NULL DEFAULT 'MERIT',  -- MERIT | PROMOTION
    status                   VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROPOSED | APPROVED | REJECTED | APPLIED
    note                     TEXT,
    proposed_by              UUID REFERENCES users(id),
    approved_by              UUID REFERENCES users(id),
    applied_record_id        UUID REFERENCES compensation_records(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_comp_proposal_cycle_employee UNIQUE (cycle_id, employee_id)
);

CREATE INDEX idx_comp_proposals_cycle ON compensation_proposals (cycle_id);
CREATE INDEX idx_comp_proposals_cycle_manager ON compensation_proposals (cycle_id, manager_employee_id);

ALTER TABLE compensation_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation_proposals FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation_proposals
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON compensation_proposals TO hris_app;

-- -----------------------------------------------------------------------------
-- 5. Permissions (global catalog) + per-tenant profile grants
-- -----------------------------------------------------------------------------
-- COMPENSATION_REVIEW = manager proposal worksheet (data-scoped to the manager's
-- reports; granted to every profile, empty for non-managers). HR reuses the
-- existing COMPENSATION_MANAGE for cycles / matrix / pools / approval / apply.

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    (gen_random_uuid(), 'COMPENSATION_REVIEW', 'COMPENSATION', 'REVIEW',
        'SELF', 'Propose compensation changes for own reports in a review cycle', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'COMPENSATION_REVIEW'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Menu items (global catalog) + per-tenant grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999953', 'menu.workspace.compReview',
        'menu.workspace.compReview', 'WORKSPACE', '/compensation/review', 'trending-up', 47, TRUE),
    ('99999999-9999-9999-9999-999999999954', 'menu.configuration.compReview',
        'menu.configuration.compReview', 'CONFIGURATION', '/settings/comp-review', 'trending-up', 48, TRUE),
    ('99999999-9999-9999-9999-999999999955', 'menu.configuration.meritMatrix',
        'menu.configuration.meritMatrix', 'CONFIGURATION', '/settings/merit-matrix', 'grid', 49, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Manager worksheet menu to every profile (data-scoped); admin menus to HR/admin.
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.compReview'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code IN ('menu.configuration.compReview', 'menu.configuration.meritMatrix')
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Seed the starter merit matrix for every existing tenant (9 cells)
-- -----------------------------------------------------------------------------
-- Flyway runs as hris_user (no RLS context), so tenant_id is set explicitly.
-- Classic shape: pay more to high performers; within a band, more to those low in
-- their pay range (BELOW) than those already ABOVE the midpoint.

INSERT INTO compensation_merit_matrix (id, rating_band, compa_band, suggested_percent, tenant_id)
SELECT gen_random_uuid(), m.rating_band, m.compa_band, m.suggested_percent, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('HIGH',  'BELOW',  6.0),
    ('HIGH',  'WITHIN', 5.0),
    ('HIGH',  'ABOVE',  3.5),
    ('SOLID', 'BELOW',  4.0),
    ('SOLID', 'WITHIN', 3.0),
    ('SOLID', 'ABOVE',  2.0),
    ('LOW',   'BELOW',  1.0),
    ('LOW',   'WITHIN', 0.5),
    ('LOW',   'ABOVE',  0.0)
) AS m(rating_band, compa_band, suggested_percent);
