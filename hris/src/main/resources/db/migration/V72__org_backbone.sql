-- =============================================================================
-- V72: Org backbone — department hierarchy, job catalog, standing teams,
--      scheduled transfers
-- =============================================================================
-- See docs/ORG_BACKBONE_DESIGN.md. The employee supervisor line is the canonical
-- hierarchy; this migration adds the vertical department structure, the job
-- catalog (strict FK, text column kept as synced denormalized copy), decouples
-- teams from projects, and adds scheduled-transfer columns mirroring the
-- scheduled-termination pattern.

-- -----------------------------------------------------------------------------
-- 1. Department hierarchy
-- -----------------------------------------------------------------------------

ALTER TABLE departments
    ADD COLUMN parent_department_id UUID REFERENCES departments(id);

CREATE INDEX idx_departments_parent ON departments (parent_department_id)
    WHERE parent_department_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. Job catalog (per-tenant config; cloned by tenant provisioning)
-- -----------------------------------------------------------------------------

CREATE TABLE job_titles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    family      VARCHAR(100),
    level       INT CHECK (level IS NULL OR (level BETWEEN 1 AND 10)),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_job_titles_tenant_name UNIQUE (tenant_id, name)
);

ALTER TABLE job_titles ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_titles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON job_titles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON job_titles TO hris_app;

-- Backfill: one catalog row per distinct existing title, per tenant.
-- Flyway runs as hris_user (no RLS context), so tenant_id comes from the rows.
INSERT INTO job_titles (name, tenant_id)
SELECT DISTINCT e.job_title, e.tenant_id
FROM employees e
WHERE e.job_title IS NOT NULL AND e.job_title <> ''
ON CONFLICT (tenant_id, name) DO NOTHING;

ALTER TABLE employees ADD COLUMN job_title_id UUID REFERENCES job_titles(id);

UPDATE employees e
SET job_title_id = jt.id
FROM job_titles jt
WHERE jt.tenant_id = e.tenant_id AND jt.name = e.job_title;

ALTER TABLE employees ALTER COLUMN job_title_id SET NOT NULL;

CREATE INDEX idx_employees_job_title ON employees (job_title_id);

-- -----------------------------------------------------------------------------
-- 3. Standing teams: project becomes optional
-- -----------------------------------------------------------------------------

ALTER TABLE teams ALTER COLUMN project_id DROP NOT NULL;

-- -----------------------------------------------------------------------------
-- 4. Scheduled transfers (mirrors employees.termination_date pattern)
-- -----------------------------------------------------------------------------

ALTER TABLE employees
    ADD COLUMN scheduled_transfer_date DATE,
    ADD COLUMN scheduled_transfer_department_id UUID REFERENCES departments(id),
    ADD COLUMN scheduled_transfer_supervisor_id UUID REFERENCES employees(id);

-- -----------------------------------------------------------------------------
-- 5. Menu items (global catalog) + per-tenant grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999943', 'menu.people.orgChart',
        'menu.people.orgChart', 'PEOPLE', '/org-chart', 'org-chart', 37, TRUE),
    ('99999999-9999-9999-9999-999999999944', 'menu.configuration.jobTitles',
        'menu.configuration.jobTitles', 'CONFIGURATION', '/settings/job-titles', 'briefcase', 38, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code IN ('menu.people.orgChart', 'menu.configuration.jobTitles')
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
