-- Notifications, audit logs, and stored dashboard snapshot rows.
-- Live analytics endpoints compute from leave/project/employee data, but the
-- director dashboard still reads leave_metrics directly.

BEGIN;

INSERT INTO notification_events (id, event_type, target_user_id, title_key, body_key, params, locale, routing_key, published_at)
VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
        'LEAVE_SUBMITTED',
        '33333333-3333-3333-3333-333333333305',
        'leave.submitted.title',
        'leave.submitted.body',
        '["Rim Ayedi","' || (CURRENT_DATE + INTERVAL '3 days')::date || '","' || (CURRENT_DATE + INTERVAL '4 days')::date || '",2]',
        'fr',
        'leave.submitted',
        date_trunc('month', NOW()) + INTERVAL '4 days 10:35'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002',
        'LEAVE_APPROVED',
        '33333333-3333-3333-3333-333333333306',
        'leave.approved.title',
        'leave.approved.body',
        '["Yasmine Trabelsi","' || CURRENT_DATE::date || '","' || (CURRENT_DATE + INTERVAL '6 days')::date || '",5]',
        'en',
        'leave.approved',
        date_trunc('month', NOW()) + INTERVAL '5 days 14:05'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003',
        'ADMIN_REQUEST_SUBMITTED',
        '33333333-3333-3333-3333-333333333302',
        'admin.submitted.title',
        'admin.submitted.body',
        '{"requesterName":"Yasmine Trabelsi","trackingNumber":"AR-SEED-0001","requestType":"Work Certificate"}',
        'fr',
        'admin.submitted',
        date_trunc('month', NOW()) + INTERVAL '3 days 09:35'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0004',
        'ADMIN_REQUEST_PROCESSED',
        '33333333-3333-3333-3333-333333333308',
        'admin.processed.title',
        'admin.processed.body',
        '{"trackingNumber":"AR-SEED-0003"}',
        'en',
        'admin.processed',
        date_trunc('month', NOW()) + INTERVAL '6 days 16:35'
    )
ON CONFLICT (id) DO UPDATE
SET event_type = EXCLUDED.event_type,
    target_user_id = EXCLUDED.target_user_id,
    title_key = EXCLUDED.title_key,
    body_key = EXCLUDED.body_key,
    params = EXCLUDED.params,
    locale = EXCLUDED.locale,
    routing_key = EXCLUDED.routing_key,
    published_at = EXCLUDED.published_at;

INSERT INTO notifications (id, user_id, title, body, is_read, created_at)
VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb1001',
        '33333333-3333-3333-3333-333333333305',
        'New leave request',
        'Rim Ayedi submitted a sick leave request that needs your approval.',
        FALSE,
        date_trunc('month', NOW()) + INTERVAL '4 days 10:40'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb1002',
        '33333333-3333-3333-3333-333333333306',
        'Leave approved',
        'Your leave request for next week has been approved.',
        FALSE,
        date_trunc('month', NOW()) + INTERVAL '5 days 14:10'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb1003',
        '33333333-3333-3333-3333-333333333308',
        'Request processed',
        'Your salary certificate request AR-SEED-0003 is ready.',
        FALSE,
        date_trunc('month', NOW()) + INTERVAL '6 days 16:40'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb1004',
        '33333333-3333-3333-3333-333333333309',
        'Administrative request rejected',
        'Your information update request was rejected because the signed form is missing.',
        TRUE,
        date_trunc('month', NOW()) + INTERVAL '2 days 15:15'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb1005',
        '33333333-3333-3333-3333-333333333302',
        'New administrative request',
        'A new work certificate request AR-SEED-0001 is waiting for processing.',
        FALSE,
        date_trunc('month', NOW()) + INTERVAL '3 days 09:40'
    )
ON CONFLICT (id) DO UPDATE
SET user_id = EXCLUDED.user_id,
    title = EXCLUDED.title,
    body = EXCLUDED.body,
    is_read = EXCLUDED.is_read,
    created_at = EXCLUDED.created_at;

INSERT INTO audit_logs (id, actor_id, action, resource, resource_id, previous_state, new_state, ip_address, timestamp)
VALUES
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0001',
        '33333333-3333-3333-3333-333333333306',
        'CREATE',
        'leave_request',
        '88888888-8888-8888-8888-888888880003',
        NULL,
        '{"status":"APPROVED","workingDays":5}',
        '10.10.0.21',
        date_trunc('month', NOW()) + INTERVAL '3 days 08:50'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0002',
        '33333333-3333-3333-3333-333333333305',
        'APPROVE',
        'approval_step',
        '99999999-9999-9999-9999-999999991003',
        NULL,
        '{"status":"APPROVED","comment":"Sprint handover accepted."}',
        '10.10.0.15',
        date_trunc('month', NOW()) + INTERVAL '5 days 13:40'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0003',
        '33333333-3333-3333-3333-333333333305',
        'REJECT',
        'approval_step',
        '99999999-9999-9999-9999-999999991004',
        NULL,
        '{"status":"REJECTED","comment":"Current deployment window cannot absorb the requested absence."}',
        '10.10.0.15',
        date_trunc('month', NOW()) + INTERVAL '8 days 16:10'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0004',
        '33333333-3333-3333-3333-333333333302',
        'UPDATE',
        'admin_request',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002',
        '{"status":"SUBMITTED"}',
        '{"status":"IN_PROGRESS"}',
        '10.10.0.11',
        date_trunc('month', NOW()) + INTERVAL '4 days 14:10'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0005',
        '33333333-3333-3333-3333-333333333302',
        'REJECT',
        'admin_request',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0004',
        '{"status":"SUBMITTED"}',
        '{"status":"REJECTED","rejectionReason":"Please attach the signed update form before resubmitting."}',
        '10.10.0.11',
        date_trunc('month', NOW()) + INTERVAL '2 days 15:10'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccc0006',
        '33333333-3333-3333-3333-333333333301',
        'EXPORT',
        'analytics_report',
        NULL,
        NULL,
        '{"reportType":"director_dashboard","format":"PDF"}',
        '10.10.0.5',
        NOW() - INTERVAL '1 day'
    )
ON CONFLICT (id) DO UPDATE
SET actor_id = EXCLUDED.actor_id,
    action = EXCLUDED.action,
    resource = EXCLUDED.resource,
    resource_id = EXCLUDED.resource_id,
    previous_state = EXCLUDED.previous_state,
    new_state = EXCLUDED.new_state,
    ip_address = EXCLUDED.ip_address,
    timestamp = EXCLUDED.timestamp;

-- Stored leave metrics are still read by the director dashboard.
INSERT INTO leave_metrics (id, period, department_id, total_requests, approved_count, rejected_count, avg_processing_days)
VALUES
    (
        'dddddddd-dddd-dddd-dddd-dddddddd1001',
        to_char(CURRENT_DATE, 'YYYY-MM'),
        '22222222-2222-2222-2222-222222222203',
        3,
        1,
        0,
        2.0
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddd1002',
        to_char(CURRENT_DATE, 'YYYY-MM'),
        '22222222-2222-2222-2222-222222222204',
        2,
        0,
        1,
        1.0
    )
ON CONFLICT (id) DO UPDATE
SET period = EXCLUDED.period,
    department_id = EXCLUDED.department_id,
    total_requests = EXCLUDED.total_requests,
    approved_count = EXCLUDED.approved_count,
    rejected_count = EXCLUDED.rejected_count,
    avg_processing_days = EXCLUDED.avg_processing_days;

-- Snapshot tables below are optional for current endpoints, but seeded for demo completeness.
INSERT INTO headcount_metrics (id, snapshot_date, department_id, total_employees, active_employees, new_hires_this_month, departures_this_month)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddd2001', CURRENT_DATE, '22222222-2222-2222-2222-222222222201', 1, 1, 0, 0),
    ('dddddddd-dddd-dddd-dddd-dddddddd2002', CURRENT_DATE, '22222222-2222-2222-2222-222222222202', 3, 2, 1, 1),
    ('dddddddd-dddd-dddd-dddd-dddddddd2003', CURRENT_DATE, '22222222-2222-2222-2222-222222222203', 2, 2, 0, 0),
    ('dddddddd-dddd-dddd-dddd-dddddddd2004', CURRENT_DATE, '22222222-2222-2222-2222-222222222204', 2, 2, 0, 0),
    ('dddddddd-dddd-dddd-dddd-dddddddd2005', CURRENT_DATE, '22222222-2222-2222-2222-222222222205', 2, 2, 1, 0)
ON CONFLICT (id) DO UPDATE
SET snapshot_date = EXCLUDED.snapshot_date,
    department_id = EXCLUDED.department_id,
    total_employees = EXCLUDED.total_employees,
    active_employees = EXCLUDED.active_employees,
    new_hires_this_month = EXCLUDED.new_hires_this_month,
    departures_this_month = EXCLUDED.departures_this_month;

INSERT INTO absence_impact_reports (id, project_id, report_date, total_absence_days, affected_members, estimated_delay_days, risk_level)
VALUES
    (
        'dddddddd-dddd-dddd-dddd-dddddddd3001',
        '66666666-6666-6666-6666-666666666601',
        CURRENT_DATE,
        5,
        1,
        1,
        'MEDIUM'
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddd3002',
        '66666666-6666-6666-6666-666666666602',
        CURRENT_DATE,
        0,
        0,
        0,
        'LOW'
    )
ON CONFLICT (id) DO UPDATE
SET project_id = EXCLUDED.project_id,
    report_date = EXCLUDED.report_date,
    total_absence_days = EXCLUDED.total_absence_days,
    affected_members = EXCLUDED.affected_members,
    estimated_delay_days = EXCLUDED.estimated_delay_days,
    risk_level = EXCLUDED.risk_level;

INSERT INTO analytics_dashboards (id, viewer_role_code, scope_type, scope_entity_id, period_start, period_end, generated_at)
VALUES
    (
        'dddddddd-dddd-dddd-dddd-dddddddd4001',
        'ADMINISTRATION',
        'ALL',
        NULL,
        date_trunc('month', CURRENT_DATE)::date,
        (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date,
        NOW() - INTERVAL '1 hour'
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddd4002',
        'HR_ADMIN',
        'ALL',
        NULL,
        date_trunc('month', CURRENT_DATE)::date,
        (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date,
        NOW() - INTERVAL '2 hours'
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddd4003',
        'DIRECTOR',
        'ALL',
        NULL,
        date_trunc('month', CURRENT_DATE)::date,
        (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date,
        NOW() - INTERVAL '3 hours'
    )
ON CONFLICT (id) DO UPDATE
SET viewer_role_code = EXCLUDED.viewer_role_code,
    scope_type = EXCLUDED.scope_type,
    scope_entity_id = EXCLUDED.scope_entity_id,
    period_start = EXCLUDED.period_start,
    period_end = EXCLUDED.period_end,
    generated_at = EXCLUDED.generated_at;

INSERT INTO export_records (id, exported_by_id, report_type, format, locale, file_path, exported_at)
VALUES
    (
        'dddddddd-dddd-dddd-dddd-dddddddd5001',
        '33333333-3333-3333-3333-333333333301',
        'director_dashboard',
        'PDF',
        'fr',
        '/tmp/hris-exports/director-dashboard-current.pdf',
        NOW() - INTERVAL '1 day'
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddd5002',
        '33333333-3333-3333-3333-333333333302',
        'audit_logs',
        'CSV',
        'en',
        '/tmp/hris-exports/audit-log-review.csv',
        NOW() - INTERVAL '2 days'
    )
ON CONFLICT (id) DO UPDATE
SET exported_by_id = EXCLUDED.exported_by_id,
    report_type = EXCLUDED.report_type,
    format = EXCLUDED.format,
    locale = EXCLUDED.locale,
    file_path = EXCLUDED.file_path,
    exported_at = EXCLUDED.exported_at;

COMMIT;
