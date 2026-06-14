-- =============================================================================
-- V75: Performance Phase 2b — 360 / peer feedback
-- =============================================================================
-- See docs/PERFORMANCE_MODULE_DESIGN.md (Phase 2 / 2b). The review's reviewer (or HR
-- with PERFORMANCE_MANAGE) nominates a panel of raters per review. Each rater rates the
-- same competency set that was snapshotted onto the subject's review (Phase 2a), on the
-- cycle's existing rating scale, plus free-text strengths/improvements. The subject sees
-- only an anonymized aggregate; the reviewer + HR see the attributed panel.
--
-- These are per-review TRANSACTIONAL rows (not per-tenant config) — they are NOT added to
-- TenantProvisioningService.TEMPLATE_TABLES. Reuses PERFORMANCE_READ / PERFORMANCE_MANAGE.
-- Multi-tenant pattern throughout: tenant_id DEFAULT from session, FORCE RLS, grants to hris_app.

-- -----------------------------------------------------------------------------
-- 1. Feedback requests (one per nominated rater, per review)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_feedback_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id           UUID NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    cycle_id            UUID NOT NULL,
    subject_employee_id UUID NOT NULL,
    subject_name        VARCHAR(200),   -- snapshot, for the rater's inbox display
    cycle_name          VARCHAR(200),   -- snapshot
    rater_employee_id   UUID NOT NULL REFERENCES employees(id),
    rater_name          VARCHAR(200),   -- snapshot, for the attributed panel
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | SUBMITTED | DECLINED
    strengths           TEXT,
    improvements        TEXT,
    requested_at        TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    reminded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id           UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_feedback_request_rater UNIQUE (review_id, rater_employee_id, tenant_id)
);

CREATE INDEX idx_feedback_requests_review ON performance_feedback_requests (review_id);
CREATE INDEX idx_feedback_requests_rater ON performance_feedback_requests (rater_employee_id);

ALTER TABLE performance_feedback_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_feedback_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_feedback_requests
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_feedback_requests TO hris_app;

-- -----------------------------------------------------------------------------
-- 2. Per-request competency ratings (snapshotted from the review at nomination)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_feedback_competency_ratings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_request_id UUID NOT NULL REFERENCES performance_feedback_requests(id) ON DELETE CASCADE,
    competency_id       UUID NOT NULL REFERENCES performance_competencies(id),
    competency_name     VARCHAR(150) NOT NULL,  -- snapshot
    category            VARCHAR(30),            -- snapshot
    rating_level_id     UUID REFERENCES performance_rating_levels(id),
    display_order       INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id           UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT uq_feedback_competency UNIQUE (feedback_request_id, competency_id, tenant_id)
);

CREATE INDEX idx_feedback_competency_request ON performance_feedback_competency_ratings (feedback_request_id);

ALTER TABLE performance_feedback_competency_ratings ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_feedback_competency_ratings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_feedback_competency_ratings
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_feedback_competency_ratings TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Menu item (global catalog) + per-tenant grants (every profile — anyone can rate)
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999949', 'menu.people.feedbackRequests',
        'menu.people.feedbackRequests', 'PEOPLE', '/performance/feedback', 'message-square', 43, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.people.feedbackRequests'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
