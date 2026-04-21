-- Leave policies, balances, requests, approval workflows, and attachments.

BEGIN;

-- Policies are seeded by leave type and contract type, not by hard-coded UUIDs.
INSERT INTO leave_policies (id, leave_type_id, contract_type, min_seniority_years, max_days_per_year, carry_over_days, carry_over_expiry)
SELECT '88888888-8888-8888-8888-888888888101', lt.id, 'PERMANENT', 0, 24, 5, 3
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (leave_type_id, contract_type, min_seniority_years) DO UPDATE
SET max_days_per_year = EXCLUDED.max_days_per_year,
    carry_over_days = EXCLUDED.carry_over_days,
    carry_over_expiry = EXCLUDED.carry_over_expiry;

INSERT INTO leave_policies (id, leave_type_id, contract_type, min_seniority_years, max_days_per_year, carry_over_days, carry_over_expiry)
SELECT '88888888-8888-8888-8888-888888888102', lt.id, 'FIXED_TERM', 0, 18, 3, 2
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (leave_type_id, contract_type, min_seniority_years) DO UPDATE
SET max_days_per_year = EXCLUDED.max_days_per_year,
    carry_over_days = EXCLUDED.carry_over_days,
    carry_over_expiry = EXCLUDED.carry_over_expiry;

INSERT INTO leave_policies (id, leave_type_id, contract_type, min_seniority_years, max_days_per_year, carry_over_days, carry_over_expiry)
SELECT '88888888-8888-8888-8888-888888888103', lt.id, 'PERMANENT', 0, 10, 0, 0
FROM leave_types lt
WHERE lt.code = 'SICK'
ON CONFLICT (leave_type_id, contract_type, min_seniority_years) DO UPDATE
SET max_days_per_year = EXCLUDED.max_days_per_year,
    carry_over_days = EXCLUDED.carry_over_days,
    carry_over_expiry = EXCLUDED.carry_over_expiry;

INSERT INTO leave_policies (id, leave_type_id, contract_type, min_seniority_years, max_days_per_year, carry_over_days, carry_over_expiry)
SELECT '88888888-8888-8888-8888-888888888104', lt.id, 'FIXED_TERM', 0, 10, 0, 0
FROM leave_types lt
WHERE lt.code = 'SICK'
ON CONFLICT (leave_type_id, contract_type, min_seniority_years) DO UPDATE
SET max_days_per_year = EXCLUDED.max_days_per_year,
    carry_over_days = EXCLUDED.carry_over_days,
    carry_over_expiry = EXCLUDED.carry_over_expiry;

INSERT INTO leave_policies (id, leave_type_id, contract_type, min_seniority_years, max_days_per_year, carry_over_days, carry_over_expiry)
SELECT '88888888-8888-8888-8888-888888888105', lt.id, 'PERMANENT', 0, 5, 0, 0
FROM leave_types lt
WHERE lt.code = 'EXCEPTIONAL'
ON CONFLICT (leave_type_id, contract_type, min_seniority_years) DO UPDATE
SET max_days_per_year = EXCLUDED.max_days_per_year,
    carry_over_days = EXCLUDED.carry_over_days,
    carry_over_expiry = EXCLUDED.carry_over_expiry;

-- Current-year balances with states that match the seeded workflows below.
INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
SELECT '88888888-8888-8888-8888-888888889001', '44444444-4444-4444-4444-444444444406', lt.id, EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 4, 0, 2, 0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days,
    version = EXCLUDED.version;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
SELECT '88888888-8888-8888-8888-888888889002', '44444444-4444-4444-4444-444444444407', lt.id, EXTRACT(YEAR FROM CURRENT_DATE)::int, 24, 1, 0, 0, 0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days,
    version = EXCLUDED.version;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
SELECT '88888888-8888-8888-8888-888888889003', '44444444-4444-4444-4444-444444444408', lt.id, EXTRACT(YEAR FROM CURRENT_DATE)::int, 10, 0, 2, 0, 0
FROM leave_types lt
WHERE lt.code = 'SICK'
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days,
    version = EXCLUDED.version;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
SELECT '88888888-8888-8888-8888-888888889004', '44444444-4444-4444-4444-444444444409', lt.id, EXTRACT(YEAR FROM CURRENT_DATE)::int, 18, 0, 2, 0, 0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days,
    version = EXCLUDED.version;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
SELECT '88888888-8888-8888-8888-888888889005', '44444444-4444-4444-4444-444444444407', lt.id, EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 0, 0, 0, 0
FROM leave_types lt
WHERE lt.code = 'EXCEPTIONAL'
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days,
    version = EXCLUDED.version;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
SELECT
    '88888888-8888-8888-8888-888888880001',
    '44444444-4444-4444-4444-444444444409',
    lt.id,
    (CURRENT_DATE + INTERVAL '15 days')::date,
    (CURRENT_DATE + INTERVAL '16 days')::date,
    2,
    'NORMAL',
    'PENDING',
    'Pending office coverage confirmation.',
    date_trunc('month', NOW()) + INTERVAL '2 days 09:00',
    0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    leave_type_id = EXCLUDED.leave_type_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    working_days = EXCLUDED.working_days,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    submitted_at = EXCLUDED.submitted_at,
    version = EXCLUDED.version;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
SELECT
    '88888888-8888-8888-8888-888888880002',
    '44444444-4444-4444-4444-444444444408',
    lt.id,
    (CURRENT_DATE + INTERVAL '3 days')::date,
    (CURRENT_DATE + INTERVAL '4 days')::date,
    2,
    'URGENT',
    'IN_APPROVAL',
    'Medical follow-up requested by physician.',
    date_trunc('month', NOW()) + INTERVAL '4 days 10:30',
    0
FROM leave_types lt
WHERE lt.code = 'SICK'
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    leave_type_id = EXCLUDED.leave_type_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    working_days = EXCLUDED.working_days,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    submitted_at = EXCLUDED.submitted_at,
    version = EXCLUDED.version;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
SELECT
    '88888888-8888-8888-8888-888888880003',
    '44444444-4444-4444-4444-444444444406',
    lt.id,
    CURRENT_DATE,
    (CURRENT_DATE + INTERVAL '6 days')::date,
    5,
    'NORMAL',
    'APPROVED',
    'Planned annual leave after sprint handover.',
    date_trunc('month', NOW()) + INTERVAL '3 days 08:45',
    0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    leave_type_id = EXCLUDED.leave_type_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    working_days = EXCLUDED.working_days,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    submitted_at = EXCLUDED.submitted_at,
    version = EXCLUDED.version;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
SELECT
    '88888888-8888-8888-8888-888888880004',
    '44444444-4444-4444-4444-444444444407',
    lt.id,
    (CURRENT_DATE + INTERVAL '8 days')::date,
    (CURRENT_DATE + INTERVAL '10 days')::date,
    3,
    'NORMAL',
    'REJECTED',
    'Urgent family matter requiring exceptional leave.',
    date_trunc('month', NOW()) + INTERVAL '7 days 11:00',
    0
FROM leave_types lt
WHERE lt.code = 'EXCEPTIONAL'
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    leave_type_id = EXCLUDED.leave_type_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    working_days = EXCLUDED.working_days,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    submitted_at = EXCLUDED.submitted_at,
    version = EXCLUDED.version;

INSERT INTO leave_requests (id, employee_id, leave_type_id, start_date, end_date, working_days, urgency_level, status, comment, submitted_at, version)
SELECT
    '88888888-8888-8888-8888-888888880005',
    '44444444-4444-4444-4444-444444444409',
    lt.id,
    (CURRENT_DATE + INTERVAL '20 days')::date,
    (CURRENT_DATE + INTERVAL '22 days')::date,
    3,
    'NORMAL',
    'CANCELLED',
    'Cancelled after staffing was reassigned.',
    date_trunc('month', NOW()) + INTERVAL '5 days 09:15',
    0
FROM leave_types lt
WHERE lt.code = 'ANNUAL'
ON CONFLICT (id) DO UPDATE
SET employee_id = EXCLUDED.employee_id,
    leave_type_id = EXCLUDED.leave_type_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    working_days = EXCLUDED.working_days,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    submitted_at = EXCLUDED.submitted_at,
    version = EXCLUDED.version;

INSERT INTO approval_workflows (id, subject_type, subject_id, status, created_at, completed_at, version)
VALUES
    ('99999999-9999-9999-9999-999999990002', 'LEAVE', '88888888-8888-8888-8888-888888880002', 'IN_PROGRESS', date_trunc('month', NOW()) + INTERVAL '4 days 10:35', NULL, 0),
    ('99999999-9999-9999-9999-999999990003', 'LEAVE', '88888888-8888-8888-8888-888888880003', 'COMPLETED', date_trunc('month', NOW()) + INTERVAL '3 days 09:00', date_trunc('month', NOW()) + INTERVAL '5 days 14:00', 0),
    ('99999999-9999-9999-9999-999999990004', 'LEAVE', '88888888-8888-8888-8888-888888880004', 'REJECTED', date_trunc('month', NOW()) + INTERVAL '7 days 11:10', date_trunc('month', NOW()) + INTERVAL '8 days 16:15', 0),
    ('99999999-9999-9999-9999-999999990005', 'LEAVE', '88888888-8888-8888-8888-888888880005', 'REJECTED', date_trunc('month', NOW()) + INTERVAL '5 days 09:20', date_trunc('month', NOW()) + INTERVAL '6 days 13:30', 0)
ON CONFLICT (id) DO UPDATE
SET subject_type = EXCLUDED.subject_type,
    subject_id = EXCLUDED.subject_id,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at,
    completed_at = EXCLUDED.completed_at,
    version = EXCLUDED.version;

INSERT INTO approval_steps (id, workflow_id, approver_id, step_order, status, context, routing_snapshot, comment, decided_at, version)
VALUES
    (
        '99999999-9999-9999-9999-999999991002',
        '99999999-9999-9999-9999-999999990002',
        '33333333-3333-3333-3333-333333333305',
        1,
        'PENDING',
        'PROJECT',
        '{"projectId":"66666666-6666-6666-6666-666666666601","role":"PROJECT_SUPERVISOR"}',
        NULL,
        NULL,
        0
    ),
    (
        '99999999-9999-9999-9999-999999991003',
        '99999999-9999-9999-9999-999999990003',
        '33333333-3333-3333-3333-333333333305',
        1,
        'APPROVED',
        'PROJECT',
        '{"projectId":"66666666-6666-6666-6666-666666666601","role":"PROJECT_SUPERVISOR"}',
        'Sprint handover accepted.',
        date_trunc('month', NOW()) + INTERVAL '5 days 13:40',
        0
    ),
    (
        '99999999-9999-9999-9999-999999991004',
        '99999999-9999-9999-9999-999999990004',
        '33333333-3333-3333-3333-333333333305',
        1,
        'REJECTED',
        'PROJECT',
        '{"projectId":"66666666-6666-6666-6666-666666666601","role":"PROJECT_SUPERVISOR"}',
        'Current deployment window cannot absorb the requested absence.',
        date_trunc('month', NOW()) + INTERVAL '8 days 16:10',
        0
    ),
    (
        '99999999-9999-9999-9999-999999991005',
        '99999999-9999-9999-9999-999999990005',
        '33333333-3333-3333-3333-333333333302',
        1,
        'REJECTED',
        'DEPARTMENT',
        '{"departmentId":"22222222-2222-2222-2222-222222222202","role":"DEPT_HEAD"}',
        'Auto-closed due to cancellation.',
        date_trunc('month', NOW()) + INTERVAL '6 days 13:20',
        0
    )
ON CONFLICT (id) DO UPDATE
SET workflow_id = EXCLUDED.workflow_id,
    approver_id = EXCLUDED.approver_id,
    step_order = EXCLUDED.step_order,
    status = EXCLUDED.status,
    context = EXCLUDED.context,
    routing_snapshot = EXCLUDED.routing_snapshot,
    comment = EXCLUDED.comment,
    decided_at = EXCLUDED.decided_at,
    version = EXCLUDED.version;

INSERT INTO file_attachments (id, request_id, file_name, mime_type, storage_path, uploaded_at, uploaded_by_id)
VALUES
    (
        '88888888-8888-8888-8888-888888887001',
        '88888888-8888-8888-8888-888888880002',
        'medical-note-april.pdf',
        'application/pdf',
        'seed/leave/88888888-8888-8888-8888-888888880002/medical-note-april.pdf',
        date_trunc('month', NOW()) + INTERVAL '4 days 10:45',
        '33333333-3333-3333-3333-333333333308'
    )
ON CONFLICT (id) DO UPDATE
SET request_id = EXCLUDED.request_id,
    file_name = EXCLUDED.file_name,
    mime_type = EXCLUDED.mime_type,
    storage_path = EXCLUDED.storage_path,
    uploaded_at = EXCLUDED.uploaded_at,
    uploaded_by_id = EXCLUDED.uploaded_by_id;

COMMIT;
