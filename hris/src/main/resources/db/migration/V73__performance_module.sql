-- =============================================================================
-- V73: Performance module — configurable rating scales, review cycles,
--      reviews, weighted goals + check-ins
-- =============================================================================
-- See docs/PERFORMANCE_MODULE_DESIGN.md. First module on the org backbone (V72).
-- The employee supervisor line (employees.supervisor_employee_id) drives review
-- routing; the reviewer is denormalized onto the review row at generation time.
-- Rating scales are per-tenant config cloned by tenant provisioning. Every table
-- follows the multi-tenant pattern: tenant_id DEFAULT from the session setting,
-- FORCE row-level security, grants to hris_app.

-- Reusable RLS helper expression (matches V66/V72):
--   USING/WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)

-- -----------------------------------------------------------------------------
-- 1. Rating scales (per-tenant config; cloned by tenant provisioning)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_rating_scales (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_rating_scales_tenant_name UNIQUE (tenant_id, name)
);

-- At most one default scale per tenant.
CREATE UNIQUE INDEX uq_rating_scales_one_default
    ON performance_rating_scales (tenant_id) WHERE is_default;

ALTER TABLE performance_rating_scales ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_rating_scales FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_rating_scales
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_rating_scales TO hris_app;

CREATE TABLE performance_rating_levels (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scale_id      UUID NOT NULL REFERENCES performance_rating_scales(id) ON DELETE CASCADE,
    label         VARCHAR(100) NOT NULL,
    numeric_value INT NOT NULL,
    display_order INT NOT NULL,
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_rating_levels_scale ON performance_rating_levels (scale_id);

ALTER TABLE performance_rating_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_rating_levels FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_rating_levels
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_rating_levels TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Review cycles
-- -----------------------------------------------------------------------------

CREATE TABLE performance_review_cycles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(200) NOT NULL,
    cycle_type              VARCHAR(30) NOT NULL,   -- ANNUAL | QUARTERLY | PROBATION | ADHOC
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | IN_REVIEW | CLOSED
    period_start            DATE NOT NULL,
    period_end              DATE NOT NULL,
    self_assessment_due     DATE,
    manager_review_due      DATE,
    opens_on                DATE,
    closes_on               DATE,
    include_sub_departments BOOLEAN NOT NULL DEFAULT FALSE,
    rating_scale_id         UUID NOT NULL REFERENCES performance_rating_scales(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id               UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_review_cycles_status ON performance_review_cycles (status);

ALTER TABLE performance_review_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_review_cycles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_review_cycles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_review_cycles TO hris_app;

CREATE TABLE performance_review_cycle_departments (
    cycle_id      UUID NOT NULL REFERENCES performance_review_cycles(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id),
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    PRIMARY KEY (cycle_id, department_id)
);

ALTER TABLE performance_review_cycle_departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_review_cycle_departments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_review_cycle_departments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_review_cycle_departments TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Reviews (one per employee per cycle)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_reviews (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id                    UUID NOT NULL REFERENCES performance_review_cycles(id),
    employee_id                 UUID NOT NULL REFERENCES employees(id),
    reviewer_employee_id        UUID REFERENCES employees(id),
    department_id               UUID REFERENCES departments(id),
    job_title                   VARCHAR(255),
    status                      VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
        -- NOT_STARTED | SELF_ASSESSMENT | MANAGER_REVIEW | PENDING_ACKNOWLEDGEMENT | COMPLETED
    self_comments               TEXT,
    manager_comments            TEXT,
    overall_rating_level_id     UUID REFERENCES performance_rating_levels(id),
    computed_score              NUMERIC(5,2),
    hr_override_rating_level_id UUID REFERENCES performance_rating_levels(id),
    hr_override_by              UUID REFERENCES users(id),
    hr_override_at              TIMESTAMPTZ,
    self_submitted_at           TIMESTAMPTZ,
    manager_submitted_at        TIMESTAMPTZ,
    acknowledged_at             TIMESTAMPTZ,
    self_reminded_at            TIMESTAMPTZ,
    manager_reminded_at         TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_reviews_cycle_employee UNIQUE (cycle_id, employee_id, tenant_id)
);

CREATE INDEX idx_reviews_employee ON performance_reviews (employee_id);
CREATE INDEX idx_reviews_reviewer ON performance_reviews (reviewer_employee_id)
    WHERE reviewer_employee_id IS NOT NULL;
CREATE INDEX idx_reviews_cycle ON performance_reviews (cycle_id);

ALTER TABLE performance_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_reviews
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_reviews TO hris_app;

-- -----------------------------------------------------------------------------
-- 4. Goals (weighted) + check-ins
-- -----------------------------------------------------------------------------

CREATE TABLE performance_goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID NOT NULL REFERENCES employees(id),
    cycle_id        UUID REFERENCES performance_review_cycles(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(30),  -- BUSINESS | DEVELOPMENT | OPERATIONAL
    weight          INT NOT NULL DEFAULT 0 CHECK (weight BETWEEN 0 AND 100),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | COMPLETED | CANCELLED
    progress_pct    INT NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    due_date        DATE,
    rating_level_id UUID REFERENCES performance_rating_levels(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id       UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_goals_employee ON performance_goals (employee_id);
CREATE INDEX idx_goals_cycle ON performance_goals (cycle_id) WHERE cycle_id IS NOT NULL;

ALTER TABLE performance_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_goals FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_goals
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_goals TO hris_app;

CREATE TABLE performance_goal_checkins (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id             UUID NOT NULL REFERENCES performance_goals(id) ON DELETE CASCADE,
    author_employee_id  UUID NOT NULL REFERENCES employees(id),
    note                TEXT,
    progress_pct        INT NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id           UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_goal_checkins_goal ON performance_goal_checkins (goal_id);

ALTER TABLE performance_goal_checkins ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_goal_checkins FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_goal_checkins
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_goal_checkins TO hris_app;

-- -----------------------------------------------------------------------------
-- 5. Analytics facts (emitted on review completion / cycle close)
-- -----------------------------------------------------------------------------

CREATE TABLE analytics_performance_facts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id             UUID NOT NULL,
    employee_id          UUID NOT NULL,
    department_id        UUID,
    job_title            VARCHAR(255),
    overall_rating_value INT,
    computed_score       NUMERIC(5,2),
    completed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id            UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_perf_facts_cycle ON analytics_performance_facts (cycle_id);
CREATE INDEX idx_perf_facts_department ON analytics_performance_facts (department_id);

ALTER TABLE analytics_performance_facts ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_performance_facts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics_performance_facts
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON analytics_performance_facts TO hris_app;

-- -----------------------------------------------------------------------------
-- 6. Permissions (global catalog) + per-tenant profile grants
-- -----------------------------------------------------------------------------

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    (gen_random_uuid(), 'PERFORMANCE_READ', 'PERFORMANCE', 'READ',
        'GLOBAL', 'View own and team performance reviews and goals', TRUE),
    (gen_random_uuid(), 'PERFORMANCE_MANAGE', 'PERFORMANCE', 'MANAGE',
        'GLOBAL', 'Configure review cycles and rating scales, HR override', TRUE)
ON CONFLICT (name) DO NOTHING;

-- PERFORMANCE_READ to every profile (all are staff-facing); PERFORMANCE_MANAGE to HR/admin.
-- Flyway runs without a tenant session setting, so tenant_id is taken from the profile row.
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'PERFORMANCE_READ'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'PERFORMANCE_MANAGE'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Menu items (global catalog) + per-tenant grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999945', 'menu.people.performance',
        'menu.people.performance', 'PEOPLE', '/performance', 'star', 39, TRUE),
    ('99999999-9999-9999-9999-999999999946', 'menu.people.teamPerformance',
        'menu.people.teamPerformance', 'PEOPLE', '/performance/team', 'users-star', 40, TRUE),
    ('99999999-9999-9999-9999-999999999947', 'menu.configuration.reviewCycles',
        'menu.configuration.reviewCycles', 'CONFIGURATION', '/performance/cycles', 'target', 41, TRUE)
ON CONFLICT (code) DO NOTHING;

-- My/Team Performance to every profile; Review Cycles to HR/admin.
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code IN ('menu.people.performance', 'menu.people.teamPerformance')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.configuration.reviewCycles'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 8. Seed a default 1-5 rating scale for every existing tenant
-- -----------------------------------------------------------------------------
-- Flyway runs as hris_user (no RLS context), so tenant_id is set explicitly.

WITH new_scales AS (
    INSERT INTO performance_rating_scales (id, name, is_default, is_active, tenant_id)
    SELECT gen_random_uuid(), 'Default 5-Point Scale', TRUE, TRUE, t.id
    FROM tenants t
    RETURNING id, tenant_id
)
INSERT INTO performance_rating_levels (scale_id, label, numeric_value, display_order, tenant_id)
SELECT s.id, lvl.label, lvl.numeric_value, lvl.display_order, s.tenant_id
FROM new_scales s
CROSS JOIN (VALUES
    ('Below Expectations', 1, 1),
    ('Partially Meets',    2, 2),
    ('Meets Expectations', 3, 3),
    ('Exceeds Expectations', 4, 4),
    ('Outstanding',        5, 5)
) AS lvl(label, numeric_value, display_order);
