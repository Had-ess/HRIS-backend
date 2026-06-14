-- =============================================================================
-- V76: Performance Phase 2c — 9-box calibration
-- =============================================================================
-- See docs/PERFORMANCE_MODULE_DESIGN.md (Phase 2 / 2c). Managers set a confidential
-- "potential" rating on the review (on the cycle's existing scale); HR opens a per-cycle
-- 9-box grid (performance x potential) and may move a review to a new cell. A move remaps
-- only the axis whose band changed to that band's representative level — performance via the
-- existing hr_override_rating_level_id, potential by overwriting potential_rating_level_id —
-- and appends a before/after audit row here. Placement is manager + HR only; never surfaced
-- to the subject. Advisory: computed_score (goal-weighted) is untouched.
--
-- Calibration rows are per-review TRANSACTIONAL data (not per-tenant config) — NOT added to
-- TenantProvisioningService.TEMPLATE_TABLES. Reuses PERFORMANCE_MANAGE (no new permission).
-- Multi-tenant pattern throughout: tenant_id DEFAULT from session, FORCE RLS, grants to hris_app.

-- -----------------------------------------------------------------------------
-- 1. Manager-set potential rating on the review (the 9-box vertical axis)
-- -----------------------------------------------------------------------------

ALTER TABLE performance_reviews
    ADD COLUMN potential_rating_level_id UUID REFERENCES performance_rating_levels(id);

-- -----------------------------------------------------------------------------
-- 2. Calibration adjustment audit trail (one row per HR move)
-- -----------------------------------------------------------------------------

CREATE TABLE performance_calibration_adjustments (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id                     UUID NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    cycle_id                      UUID NOT NULL,
    previous_performance_level_id UUID REFERENCES performance_rating_levels(id),
    new_performance_level_id      UUID REFERENCES performance_rating_levels(id),
    previous_potential_level_id   UUID REFERENCES performance_rating_levels(id),
    new_potential_level_id        UUID REFERENCES performance_rating_levels(id),
    note                          TEXT,
    adjusted_by                   UUID REFERENCES users(id),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id                     UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id)
);

CREATE INDEX idx_calibration_adjustments_review ON performance_calibration_adjustments (review_id);
CREATE INDEX idx_calibration_adjustments_cycle ON performance_calibration_adjustments (cycle_id);

ALTER TABLE performance_calibration_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE performance_calibration_adjustments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON performance_calibration_adjustments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON performance_calibration_adjustments TO hris_app;

-- -----------------------------------------------------------------------------
-- 3. Potential dimension on the analytics fact (feeds the calibration grid analytics)
-- -----------------------------------------------------------------------------

ALTER TABLE analytics_performance_facts
    ADD COLUMN potential_rating_value INT;

-- -----------------------------------------------------------------------------
-- 4. Menu item (global catalog) + per-tenant grants (HR / admin only)
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999950', 'menu.configuration.calibration',
        'menu.configuration.calibration', 'CONFIGURATION', '/performance/calibration', 'grid', 44, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.configuration.calibration'
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
