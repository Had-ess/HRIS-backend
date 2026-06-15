-- =============================================================================
-- V81: Recruitment module Phase 1 — ATS core spine
--      requisitions + candidate pool + applications/pipeline + hire handoff
-- =============================================================================
-- See docs/RECRUITMENT_MODULE_DESIGN.md. The hiring front-door: requisition ->
-- candidate -> application -> pipeline -> HIRED -> new-hire handoff into the
-- existing employee-creation flow. Requisition approval rides the shared approval
-- engine (subjectType 'REQUISITION'). Multi-tenant throughout: tenant_id DEFAULT
-- from the session setting, ENABLE + FORCE row-level security, grants to hris_app.
-- Menus are global; profile_* grants are per-tenant (cloned for new tenants by
-- TenantProvisioningService), matching V72/V73/V77.

-- Reusable RLS helper expression (matches V66/V72/V73/V77):
--   USING/WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)

-- -----------------------------------------------------------------------------
-- 1. Requisitions (open roles)
-- -----------------------------------------------------------------------------

CREATE TABLE recruitment_requisitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                       VARCHAR(255) NOT NULL,
    job_title_id                UUID NOT NULL REFERENCES job_titles(id),
    department_id               UUID NOT NULL REFERENCES departments(id),
    hiring_manager_employee_id  UUID NOT NULL REFERENCES employees(id),
    pay_grade_id                UUID REFERENCES compensation_pay_grades(id),
    employment_type             VARCHAR(50) NOT NULL
        CHECK (employment_type IN ('PERMANENT','FIXED_TERM','INTERNSHIP','CONTRACTOR')),
    location                    VARCHAR(100),
    headcount                   INT NOT NULL DEFAULT 1 CHECK (headcount >= 1),
    filled_count                INT NOT NULL DEFAULT 0 CHECK (filled_count >= 0),
    description                 TEXT,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','OPEN','ON_HOLD','FILLED','CLOSED','CANCELLED')),
    opened_at                   TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    created_by_id               UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                   UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_requisitions_status ON recruitment_requisitions (status);
CREATE INDEX idx_requisitions_department ON recruitment_requisitions (department_id);
CREATE INDEX idx_requisitions_hiring_manager ON recruitment_requisitions (hiring_manager_employee_id);

ALTER TABLE recruitment_requisitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment_requisitions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recruitment_requisitions
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment_requisitions TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Candidates (recruiter-entered talent pool; one record per email per tenant)
-- -----------------------------------------------------------------------------

CREATE TABLE recruitment_candidates (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    phone            VARCHAR(50),
    source           VARCHAR(30) NOT NULL DEFAULT 'DIRECT'
        CHECK (source IN ('REFERRAL','JOB_BOARD','LINKEDIN','AGENCY','DIRECT','INTERNAL','OTHER')),
    current_title    VARCHAR(255),
    current_company  VARCHAR(255),
    location         VARCHAR(100),
    resume_url       VARCHAR(1000),
    notes            TEXT,
    created_by_id    UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id        UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_candidate_email_tenant UNIQUE (tenant_id, email)
);

ALTER TABLE recruitment_candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment_candidates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recruitment_candidates
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment_candidates TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Applications (candidate <-> requisition pipeline record)
-- -----------------------------------------------------------------------------

CREATE TABLE recruitment_applications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requisition_id    UUID NOT NULL REFERENCES recruitment_requisitions(id) ON DELETE CASCADE,
    candidate_id      UUID NOT NULL REFERENCES recruitment_candidates(id) ON DELETE CASCADE,
    stage             VARCHAR(20) NOT NULL DEFAULT 'APPLIED'
        CHECK (stage IN ('APPLIED','SCREENING','INTERVIEW','OFFER','HIRED','REJECTED','WITHDRAWN')),
    rating            SMALLINT CHECK (rating BETWEEN 1 AND 5),
    rejection_reason  VARCHAR(255),
    source            VARCHAR(30) NOT NULL DEFAULT 'DIRECT'
        CHECK (source IN ('REFERRAL','JOB_BOARD','LINKEDIN','AGENCY','DIRECT','INTERNAL','OTHER')),
    applied_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stage_changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    hired_at          TIMESTAMPTZ,
    created_by_id     UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id         UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_application_req_candidate UNIQUE (tenant_id, requisition_id, candidate_id)
);

CREATE INDEX idx_applications_requisition ON recruitment_applications (requisition_id);
CREATE INDEX idx_applications_candidate ON recruitment_applications (candidate_id);
CREATE INDEX idx_applications_stage ON recruitment_applications (stage);

ALTER TABLE recruitment_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment_applications FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recruitment_applications
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment_applications TO hris_app;

-- -----------------------------------------------------------------------------
-- 4. Application stage history (pipeline audit trail)
-- -----------------------------------------------------------------------------

CREATE TABLE recruitment_application_stage_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES recruitment_applications(id) ON DELETE CASCADE,
    from_stage      VARCHAR(20),
    to_stage        VARCHAR(20) NOT NULL,
    note            VARCHAR(500),
    changed_by_id   UUID,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id       UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_stage_history_application ON recruitment_application_stage_history (application_id);

ALTER TABLE recruitment_application_stage_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment_application_stage_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recruitment_application_stage_history
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment_application_stage_history TO hris_app;

-- -----------------------------------------------------------------------------
-- 5. New-hire handoff (bridge into the employee-creation flow)
-- -----------------------------------------------------------------------------

CREATE TABLE recruitment_new_hires (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       UUID NOT NULL UNIQUE REFERENCES recruitment_applications(id) ON DELETE CASCADE,
    candidate_id         UUID NOT NULL,
    requisition_id       UUID NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
    target_start_date    DATE,
    created_employee_id  UUID REFERENCES employees(id),
    finalized_at         TIMESTAMPTZ,
    finalized_by_id      UUID,
    created_by_id        UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id            UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_new_hires_status ON recruitment_new_hires (status);

ALTER TABLE recruitment_new_hires ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment_new_hires FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recruitment_new_hires
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment_new_hires TO hris_app;

-- -----------------------------------------------------------------------------
-- 6. Permissions (global catalog) + per-tenant profile grants
-- -----------------------------------------------------------------------------

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    (gen_random_uuid(), 'RECRUITMENT_MANAGE', 'RECRUITMENT', 'MANAGE',
        'GLOBAL', 'Full recruiting workspace: requisitions, candidates, pipeline, hire handoff', TRUE),
    (gen_random_uuid(), 'RECRUITMENT_REQUEST', 'RECRUITMENT', 'REQUEST',
        'GLOBAL', 'Raise and track own requisitions', TRUE),
    (gen_random_uuid(), 'RECRUITMENT_APPROVE', 'RECRUITMENT', 'APPROVE',
        'GLOBAL', 'HR fallback approver for requisition workflows', TRUE)
ON CONFLICT (name) DO NOTHING;

-- RECRUITMENT_MANAGE -> HR/admin
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'RECRUITMENT_MANAGE'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- RECRUITMENT_REQUEST -> managers + HR/admin
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'RECRUITMENT_REQUEST'
WHERE p.code IN ('MANAGER', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- RECRUITMENT_APPROVE -> HR/admin (fallback approver)
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'RECRUITMENT_APPROVE'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Menu items (global catalog, PEOPLE section) + per-tenant grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999961', 'menu.people.recruitment',
        'menu.people.recruitment', 'PEOPLE', '/recruitment', 'briefcase', 61, TRUE),
    ('99999999-9999-9999-9999-999999999962', 'menu.people.candidates',
        'menu.people.candidates', 'PEOPLE', '/recruitment/candidates', 'user-plus', 62, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Recruitment workspace -> managers + HR/admin (REQUEST + MANAGE holders)
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.people.recruitment'
WHERE p.code IN ('MANAGER', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

-- Candidate pool -> HR/admin
INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.people.candidates'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
