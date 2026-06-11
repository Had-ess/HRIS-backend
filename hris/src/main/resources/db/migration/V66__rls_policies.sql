-- =============================================================================
-- V66: Multi-tenancy cutover — runtime roles + Row-Level Security
-- =============================================================================
-- From this point the application connects as hris_app (NOT the table owner),
-- and the database itself refuses cross-tenant rows. The tenant_id DEFAULT
-- flips from the transition constant to the connection's tenant setting:
-- fail-closed, an insert without tenant context violates NOT NULL.
--
-- Role notes:
--   hris_app          runtime role; RLS policies apply (FORCE on every table)
--   hris_maintenance  BYPASSRLS; reserved for future cross-tenant operations
--   migrations        keep running as the owner (dev: the docker superuser).
--                     In prod the Flyway role needs BYPASSRLS. Data-fixing
--                     migrations that touch tenant tables must SET
--                     app.current_tenant themselves (FORCE applies to owners).
--
-- Dev-default passwords; rotate outside local development (see prod profile).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hris_app') THEN
        CREATE ROLE hris_app LOGIN PASSWORD 'hris_app_pass';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hris_maintenance') THEN
        CREATE ROLE hris_maintenance LOGIN PASSWORD 'hris_maintenance_pass' BYPASSRLS;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO hris_app, hris_maintenance;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO hris_app, hris_maintenance;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO hris_app, hris_maintenance;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hris_app, hris_maintenance;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO hris_app, hris_maintenance;

DO $$
DECLARE
    t text;
    -- user_action_tokens is deliberately EXCLUDED from RLS: activation/reset
    -- links are opened with no session, so the tenant must be derivable from
    -- the row itself; the unguessable 256-bit token hash is the access
    -- control. Its tenant_id default still flips below.
    rls_tables CONSTANT text[] := ARRAY[
        'users', 'user_credentials', 'user_profile_assignments',
        'access_profiles', 'profile_permissions', 'profile_menu_access', 'profile_assignment_rules',
        'employees', 'employee_status_history', 'employee_department_history',
        'departments', 'teams', 'team_hierarchy_relations', 'team_project_links',
        'projects', 'project_assignments', 'project_departments',
        'work_schedules', 'hr_calendars', 'hr_holidays', 'public_holidays', 'enterprise_settings',
        'leave_types', 'leave_policies', 'leave_acquisition_policies', 'leave_accrual_runs',
        'leave_balances', 'leave_balance_transactions', 'leave_requests', 'file_attachments',
        'validation_workflows', 'approval_workflows', 'approval_steps',
        'admin_request_types', 'admin_requests', 'admin_request_comments', 'admin_request_attachments',
        'notifications', 'notification_events', 'audit_logs', 'export_records',
        'analytics_events',
        'analytics_approval_facts', 'analytics_headcount_facts',
        'analytics_leave_facts', 'analytics_project_absence_facts',
        'analytics_approval_bottleneck_snapshots', 'analytics_headcount_metrics_snapshots',
        'analytics_leave_distribution_snapshots', 'analytics_leave_metrics_snapshots'
    ];
BEGIN
    FOREACH t IN ARRAY rls_tables
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
                WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid)
            $p$, t);
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT current_setting(''app.current_tenant'', true)::uuid', t);
    END LOOP;

    -- default flip only (no policy) for the RLS-exempt token table
    EXECUTE 'ALTER TABLE user_action_tokens ALTER COLUMN tenant_id SET DEFAULT current_setting(''app.current_tenant'', true)::uuid';
END $$;
