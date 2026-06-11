-- =============================================================================
-- V67: RLS hardening — treat an empty tenant setting as "no tenant"
-- =============================================================================
-- After SET + RESET, current_setting('app.current_tenant', true) yields ''
-- (empty string), not NULL — and ''::uuid raises "invalid input syntax"
-- instead of failing closed. NULLIF folds both unset states to NULL, making
-- the policy predicate NULL → zero rows, and the column default NULL →
-- NOT NULL violation. Replaces the V66 policy/default expressions.

DO $$
DECLARE
    t text;
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
        EXECUTE format('DROP POLICY tenant_isolation ON %I', t);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
                WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
            $p$, t);
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid', t);
    END LOOP;

    EXECUTE 'ALTER TABLE user_action_tokens ALTER COLUMN tenant_id SET DEFAULT NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid';
END $$;
