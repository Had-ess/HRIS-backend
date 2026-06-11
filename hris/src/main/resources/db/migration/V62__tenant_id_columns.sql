-- =============================================================================
-- V62: Multi-tenancy phase 1 — tenant_id on every tenant-scoped table
-- =============================================================================
-- Backfills all rows into the default tenant. The column DEFAULT is the
-- default tenant id during the transition window so the unchanged application
-- keeps inserting valid rows; the V64 cutover flips the DEFAULT to
-- current_setting('app.current_tenant') (fail-closed).
--
-- Global platform tables intentionally excluded: permissions, menu_items,
-- signing_keys, oauth2_authorization, spring_session(_attributes), shedlock,
-- flyway_schema_history. Tenancy of sessions/authorizations rides inside the
-- principal (docs/TENANCY_DESIGN.md §6).

DO $$
DECLARE
    default_tenant CONSTANT uuid := '00000000-0000-0000-0000-000000000001';
    t text;
    tenant_tables CONSTANT text[] := ARRAY[
        -- identity & access
        'users', 'user_credentials', 'user_action_tokens', 'user_profile_assignments',
        'access_profiles', 'profile_permissions', 'profile_menu_access', 'profile_assignment_rules',
        -- organisation
        'employees', 'employee_status_history', 'employee_department_history',
        'departments', 'teams', 'team_hierarchy_relations', 'team_project_links',
        'projects', 'project_assignments', 'project_departments',
        'work_schedules', 'hr_calendars', 'hr_holidays', 'public_holidays', 'enterprise_settings',
        -- leave
        'leave_types', 'leave_policies', 'leave_acquisition_policies', 'leave_accrual_runs',
        'leave_balances', 'leave_balance_transactions', 'leave_requests', 'file_attachments',
        -- approvals
        'validation_workflows', 'approval_workflows', 'approval_steps',
        -- admin requests
        'admin_request_types', 'admin_requests', 'admin_request_comments', 'admin_request_attachments',
        -- platform-adjacent per-tenant data
        'notifications', 'notification_events', 'audit_logs', 'export_records',
        -- analytics
        'analytics_events',
        'analytics_approval_facts', 'analytics_headcount_facts',
        'analytics_leave_facts', 'analytics_project_absence_facts',
        'analytics_approval_bottleneck_snapshots', 'analytics_headcount_metrics_snapshots',
        'analytics_leave_distribution_snapshots', 'analytics_leave_metrics_snapshots'
    ];
BEGIN
    FOREACH t IN ARRAY tenant_tables
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id UUID', t);
        EXECUTE format('UPDATE %I SET tenant_id = %L', t, default_tenant);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', t);
        -- Transition default; V64 replaces it with current_setting-based default.
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT %L::uuid', t, default_tenant);
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT fk_%s_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)', t, t);
    END LOOP;
END $$;

-- Plain tenant index on high-row tables (config tables get composite uniques
-- led by tenant_id in V63, which double as their tenant index).
CREATE INDEX idx_employees_tenant                  ON employees (tenant_id);
CREATE INDEX idx_employee_status_history_tenant    ON employee_status_history (tenant_id);
CREATE INDEX idx_employee_department_history_tenant ON employee_department_history (tenant_id);
CREATE INDEX idx_user_profile_assignments_tenant   ON user_profile_assignments (tenant_id);
CREATE INDEX idx_team_hierarchy_relations_tenant   ON team_hierarchy_relations (tenant_id);
CREATE INDEX idx_project_assignments_tenant        ON project_assignments (tenant_id);
CREATE INDEX idx_leave_requests_tenant             ON leave_requests (tenant_id);
CREATE INDEX idx_leave_balances_tenant             ON leave_balances (tenant_id);
CREATE INDEX idx_leave_balance_transactions_tenant ON leave_balance_transactions (tenant_id);
CREATE INDEX idx_file_attachments_tenant           ON file_attachments (tenant_id);
CREATE INDEX idx_approval_workflows_tenant         ON approval_workflows (tenant_id);
CREATE INDEX idx_approval_steps_tenant             ON approval_steps (tenant_id);
CREATE INDEX idx_admin_requests_tenant             ON admin_requests (tenant_id);
CREATE INDEX idx_notifications_tenant              ON notifications (tenant_id);
CREATE INDEX idx_notification_events_tenant        ON notification_events (tenant_id);
CREATE INDEX idx_audit_logs_tenant                 ON audit_logs (tenant_id);
CREATE INDEX idx_export_records_tenant             ON export_records (tenant_id);
CREATE INDEX idx_analytics_events_tenant           ON analytics_events (tenant_id);
