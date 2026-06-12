-- =============================================================================
-- V69: Time & attendance — declarative weekly timesheets
-- =============================================================================
-- See docs/TIME_ATTENDANCE_DESIGN.md. Tables are tenant-scoped from birth:
-- tenant_id DEFAULT from the session setting (NULLIF pattern, V67) + FORCE RLS.
-- Profile grants join through access_profiles so EVERY existing tenant's
-- profiles receive the new permissions/menus, not just the default tenant.

-- -----------------------------------------------------------------------------
-- 1. Tables
-- -----------------------------------------------------------------------------

CREATE TABLE timesheets (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id        UUID NOT NULL REFERENCES employees(id),
    period_start       DATE NOT NULL,
    period_end         DATE NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_minutes      INTEGER NOT NULL DEFAULT 0,
    submitted_at       TIMESTAMPTZ,
    decided_at         TIMESTAMPTZ,
    decided_by_user_id UUID,
    rejection_reason   VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id          UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT ck_timesheets_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_timesheets_week
        CHECK (EXTRACT(ISODOW FROM period_start) = 1 AND period_end = period_start + 6),
    CONSTRAINT uq_timesheets_employee_week UNIQUE (tenant_id, employee_id, period_start)
);

CREATE INDEX idx_timesheets_employee ON timesheets (tenant_id, employee_id, period_start DESC);
CREATE INDEX idx_timesheets_status ON timesheets (tenant_id, status);

CREATE TABLE timesheet_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timesheet_id UUID NOT NULL REFERENCES timesheets(id) ON DELETE CASCADE,
    work_date    DATE NOT NULL,
    project_id   UUID REFERENCES projects(id),
    category     VARCHAR(20) NOT NULL,
    minutes      INTEGER NOT NULL,
    note         VARCHAR(500),
    tenant_id    UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT ck_timesheet_entries_category
        CHECK (category IN ('PROJECT', 'MEETING', 'TRAINING', 'ADMIN', 'SUPPORT', 'OTHER')),
    CONSTRAINT ck_timesheet_entries_minutes CHECK (minutes > 0)
);

CREATE INDEX idx_timesheet_entries_sheet ON timesheet_entries (timesheet_id);

-- -----------------------------------------------------------------------------
-- 2. RLS (same idiom as V66/V67; grants come from V66 default privileges)
-- -----------------------------------------------------------------------------

ALTER TABLE timesheets ENABLE ROW LEVEL SECURITY;
ALTER TABLE timesheets FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON timesheets
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE timesheet_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE timesheet_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON timesheet_entries
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------------
-- 3. Permissions (global catalog)
-- -----------------------------------------------------------------------------

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555991', 'TIMESHEET_MANAGE_OWN', 'TIMESHEET', 'MANAGE_OWN',
        'OWN', 'Create, edit and submit own weekly timesheets', TRUE),
    ('55555555-5555-5555-5555-555555555992', 'TIMESHEET_READ', 'TIMESHEET', 'READ',
        'SCOPED', 'Read timesheets of employees in scope', TRUE),
    ('55555555-5555-5555-5555-555555555993', 'TIMESHEET_APPROVE', 'TIMESHEET', 'APPROVE',
        'SCOPED', 'Approve or reject submitted timesheets in scope', TRUE)
ON CONFLICT (name) DO NOTHING;

-- Per-tenant grants: access_profiles carry tenant_id, so the SELECT fans out
-- to every tenant that has the profile (default + already-provisioned ones).
INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'TIMESHEET_MANAGE_OWN'
WHERE p.code IN ('SELF_SERVICE', 'MANAGER_INBOX', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name IN ('TIMESHEET_READ', 'TIMESHEET_APPROVE')
WHERE p.code IN ('MANAGER_INBOX', 'TEAM_APPROVER_PROFILE', 'DEPT_APPROVER_PROFILE',
                 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Menu items (global catalog) + per-tenant menu grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999940', 'menu.workspace.myTimesheets',
        'menu.workspace.myTimesheets', 'WORKSPACE', '/timesheets', 'clock', 35, TRUE),
    ('99999999-9999-9999-9999-999999999941', 'menu.workspace.timesheetApprovals',
        'menu.workspace.timesheetApprovals', 'WORKSPACE', '/timesheet-approvals', 'check-circle', 55, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.myTimesheets'
WHERE p.code IN ('SELF_SERVICE', 'MANAGER_INBOX', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.timesheetApprovals'
WHERE p.code IN ('MANAGER_INBOX', 'TEAM_APPROVER_PROFILE', 'DEPT_APPROVER_PROFILE',
                 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
