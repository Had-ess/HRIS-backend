-- =============================================================================
-- V74: Performance Phase 2a — competency framework
-- =============================================================================
-- See docs/PERFORMANCE_MODULE_DESIGN.md (Phase 2 / 2a). Competencies are per-tenant
-- config (cloned by tenant provisioning like rating scales). A competency is either
-- CORE (applies to everyone) or mapped to one or more job families (job_titles.family).
-- Managers rate competencies on the cycle's existing rating scale; ratings are
-- snapshotted onto the review and are advisory (they do NOT feed computed_score).
-- Reuses PERFORMANCE_READ / PERFORMANCE_MANAGE — no new permissions.
-- Multi-tenant pattern throughout: tenant_id DEFAULT from session, FORCE RLS,
-- grants to hris_app.

-- -----------------------------------------------------------------------------
-- 1. Competency catalog (per-tenant config; cloned by tenant provisioning)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_competencies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    category    VARCHAR(30),  -- CORE | LEADERSHIP | TECHNICAL | FUNCTIONAL | BEHAVIORAL
    is_core     BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_competencies_tenant_name UNIQUE (tenant_id, name)
);

ALTER TABLE performance_competencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_competencies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_competencies
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_competencies TO hris_app;

-- Family mappings (only meaningful when is_core = false). One row per (competency, family).
CREATE TABLE performance_competency_job_families (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competency_id UUID NOT NULL REFERENCES performance_competencies(id) ON DELETE CASCADE,
    job_family    VARCHAR(150) NOT NULL,
    tenant_id     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_competency_family UNIQUE (competency_id, job_family)
);

CREATE INDEX idx_competency_families_competency ON performance_competency_job_families (competency_id);
CREATE INDEX idx_competency_families_family ON performance_competency_job_families (job_family);

ALTER TABLE performance_competency_job_families ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_competency_job_families FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_competency_job_families
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_competency_job_families TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Per-review competency ratings (snapshotted at generation)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_review_competencies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id       UUID NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    competency_id   UUID NOT NULL REFERENCES performance_competencies(id),
    competency_name VARCHAR(150) NOT NULL,  -- snapshot, stable even if the catalog is edited later
    category        VARCHAR(30),            -- snapshot
    rating_level_id UUID REFERENCES performance_rating_levels(id),
    comments        TEXT,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id       UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_review_competency UNIQUE (review_id, competency_id, tenant_id)
);

CREATE INDEX idx_review_competencies_review ON performance_review_competencies (review_id);

ALTER TABLE performance_review_competencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_review_competencies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_review_competencies
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_review_competencies TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Menu item (global catalog) + per-tenant grants (HR/admin only)
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999948', 'menu.configuration.competencies',
        'menu.configuration.competencies', 'CONFIGURATION', '/performance/competencies', 'award', 42, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.configuration.competencies'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Seed a starter set of CORE competencies for every existing tenant
-- -----------------------------------------------------------------------------
-- Flyway runs as hris_user (no RLS context), so tenant_id is set explicitly.

INSERT INTO performance_competencies (id, name, description, category, is_core, is_active, tenant_id)
SELECT gen_random_uuid(), c.name, c.description, 'CORE', TRUE, TRUE, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('Communication',  'Shares information clearly and listens actively across the team.'),
    ('Collaboration',  'Works effectively with others toward shared goals.'),
    ('Ownership',      'Takes responsibility for outcomes and follows through on commitments.'),
    ('Adaptability',   'Responds constructively to change and new priorities.')
) AS c(name, description)
ON CONFLICT (tenant_id, name) DO NOTHING;
