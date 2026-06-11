-- =============================================================================
-- V63: Multi-tenancy phase 1 — business uniques become per-tenant
-- =============================================================================
-- Two tenants may both have a department "ENG", a leave type "ANNUAL", or the
-- same user email. Uniques whose key already contains a UUID FK (leave_balances,
-- profile_permissions, hr_holidays, ...) stay unchanged: parent ids are unique
-- across tenants, so no cross-tenant collision is possible.
--
-- Deliberately untouched globals: user_action_tokens.token_hash (random 256-bit,
-- looked up before tenant context exists), notifications.event_id (outbox dedup,
-- random UUID), permissions/menu_items (platform catalog).

-- identity
ALTER TABLE users DROP CONSTRAINT uq_users_email;
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email);

-- organisation
ALTER TABLE departments DROP CONSTRAINT uq_departments_code;
ALTER TABLE departments ADD CONSTRAINT uq_departments_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE teams DROP CONSTRAINT uq_teams_code;
ALTER TABLE teams ADD CONSTRAINT uq_teams_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE projects DROP CONSTRAINT uq_projects_code;
ALTER TABLE projects ADD CONSTRAINT uq_projects_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE employees DROP CONSTRAINT uq_employees_employee_code;
ALTER TABLE employees ADD CONSTRAINT uq_employees_tenant_employee_code UNIQUE (tenant_id, employee_code);

ALTER TABLE hr_calendars DROP CONSTRAINT uq_hr_calendars_code;
ALTER TABLE hr_calendars ADD CONSTRAINT uq_hr_calendars_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE enterprise_settings DROP CONSTRAINT uq_enterprise_settings_singleton;
ALTER TABLE enterprise_settings ADD CONSTRAINT uq_enterprise_settings_tenant_singleton UNIQUE (tenant_id, singleton_key);

-- leave configuration
ALTER TABLE leave_types DROP CONSTRAINT uq_leave_types_code;
ALTER TABLE leave_types ADD CONSTRAINT uq_leave_types_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE leave_acquisition_policies DROP CONSTRAINT uq_leave_acquisition_policies_code;
ALTER TABLE leave_acquisition_policies ADD CONSTRAINT uq_leave_acquisition_policies_tenant_code UNIQUE (tenant_id, code);

-- approvals / requests
ALTER TABLE validation_workflows DROP CONSTRAINT uq_validation_workflows_code;
ALTER TABLE validation_workflows ADD CONSTRAINT uq_validation_workflows_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE admin_request_types DROP CONSTRAINT uq_admin_request_types_code;
ALTER TABLE admin_request_types ADD CONSTRAINT uq_admin_request_types_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE admin_requests DROP CONSTRAINT uq_admin_requests_request_number;
ALTER TABLE admin_requests ADD CONSTRAINT uq_admin_requests_tenant_request_number UNIQUE (tenant_id, request_number);

-- access profiles
ALTER TABLE access_profiles DROP CONSTRAINT uq_access_profiles_code;
ALTER TABLE access_profiles ADD CONSTRAINT uq_access_profiles_tenant_code UNIQUE (tenant_id, code);

-- analytics snapshots: scope keys (snapshot_date, scope_type, scope_id) repeat
-- per tenant, so the tenant joins the key.
ALTER TABLE analytics_approval_bottleneck_snapshots DROP CONSTRAINT uq_analytics_approval_bottleneck_snapshot;
ALTER TABLE analytics_approval_bottleneck_snapshots
    ADD CONSTRAINT uq_analytics_approval_bottleneck_snapshot
    UNIQUE (tenant_id, snapshot_date, scope_type, scope_id, source_type, approver_level);

ALTER TABLE analytics_headcount_metrics_snapshots DROP CONSTRAINT uq_analytics_headcount_metrics_snapshot;
ALTER TABLE analytics_headcount_metrics_snapshots
    ADD CONSTRAINT uq_analytics_headcount_metrics_snapshot
    UNIQUE (tenant_id, snapshot_date, scope_type, scope_id);

ALTER TABLE analytics_leave_distribution_snapshots DROP CONSTRAINT uq_analytics_leave_distribution_snapshot;
ALTER TABLE analytics_leave_distribution_snapshots
    ADD CONSTRAINT uq_analytics_leave_distribution_snapshot
    UNIQUE (tenant_id, snapshot_date, scope_type, scope_id, leave_type_id);

ALTER TABLE analytics_leave_metrics_snapshots DROP CONSTRAINT uq_analytics_leave_metrics_snapshot;
ALTER TABLE analytics_leave_metrics_snapshots
    ADD CONSTRAINT uq_analytics_leave_metrics_snapshot
    UNIQUE (tenant_id, snapshot_date, scope_type, scope_id);
