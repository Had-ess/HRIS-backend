-- ============================================================================
-- V37__report_aligned_screenshot_seed.sql
--
-- Heavy demo seed aligned with the ST2i HRIS final-report content
-- (project window: 2026-02-02 to 2026-05-15, screenshot capture: 2026-05-22).
--
-- Provides screenshot-ready data for:
--   * Sprint 3 leave management   (15 leave requests Feb-May 2026, 4 statuses)
--   * Sprint 4 administrative req (12 requests across all 7 lifecycle states)
--   * Sprint 5 monitoring         (analytics snapshots for Feb-May 2026)
--   * HR Calendar                 (full Tunisian 2026 public holidays)
--   * Accrual Runs                (one COMPLETED run per Feb/Mar/Apr/May 2026)
--   * Audit Log                   (entries spanning LOW/MEDIUM/HIGH/CRITICAL)
--   * Notifications               (per event type from report Sprint 5)
--
-- All inserts are idempotent.  Existing CURRENT_DATE-anchored demo rows are
-- left intact; this file only ADDS rows with deterministic dates so trend
-- charts populate Feb-May 2026.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Tunisian 2026 public holidays (HR Calendar widget + Next Holiday tile)
-- ----------------------------------------------------------------------------
INSERT INTO hr_holidays (id, calendar_id, date, name, is_recurring, created_at, updated_at)
VALUES
    ('37000001-0001-0001-0001-000000000101', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-01-01', 'New Year''s Day',              TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000102', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-01-14', 'Revolution Day',               TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000103', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-03-20', 'Independence Day',             TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000104', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-03-21', 'Eid al-Fitr (day 1)',          FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000105', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-03-22', 'Eid al-Fitr (day 2)',          FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000106', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-04-09', 'Martyrs'' Day',                TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000107', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-05-01', 'Labour Day',                   TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000108', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-05-28', 'Eid al-Adha (day 1)',          FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000109', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-05-29', 'Eid al-Adha (day 2)',          FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000110', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-06-17', 'Islamic New Year',             FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-07-25', 'Republic Day',                 TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000112', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-08-13', 'Women''s Day',                 TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000113', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-08-26', 'Mawlid (Prophet''s Birthday)', FALSE, NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000114', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-10-15', 'Evacuation Day',               TRUE,  NOW(), NOW()),
    ('37000001-0001-0001-0001-000000000115', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-12-25', 'Christmas Day',                TRUE,  NOW(), NOW())
ON CONFLICT (calendar_id, date, name) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Monthly accrual runs (one COMPLETED run for each of Feb / Mar / Apr / May 2026)
--    Populates the Accrual Runs admin list with multiple historical entries.
-- ----------------------------------------------------------------------------
INSERT INTO leave_accrual_runs (
    id, run_date, started_at, finished_at, status, policies_processed,
    employees_processed, transactions_created, error_message, triggered_by, triggered_by_user_id
)
VALUES
    ('37000002-0002-0002-0002-000000000001', DATE '2026-02-01',
        TIMESTAMPTZ '2026-02-01 02:00:00+00', TIMESTAMPTZ '2026-02-01 02:00:42+00',
        'COMPLETED', 1, 12, 12, NULL, 'SYSTEM', NULL),
    ('37000002-0002-0002-0002-000000000002', DATE '2026-03-01',
        TIMESTAMPTZ '2026-03-01 02:00:00+00', TIMESTAMPTZ '2026-03-01 02:00:38+00',
        'COMPLETED', 1, 12, 12, NULL, 'SYSTEM', NULL),
    ('37000002-0002-0002-0002-000000000003', DATE '2026-04-01',
        TIMESTAMPTZ '2026-04-01 02:00:00+00', TIMESTAMPTZ '2026-04-01 02:00:45+00',
        'COMPLETED', 1, 12, 12, NULL, 'SYSTEM', NULL),
    ('37000002-0002-0002-0002-000000000004', DATE '2026-05-01',
        TIMESTAMPTZ '2026-05-01 02:00:00+00', TIMESTAMPTZ '2026-05-01 02:00:51+00',
        'COMPLETED', 1, 12, 12, NULL, 'SYSTEM',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local')),
    ('37000002-0002-0002-0002-000000000005', DATE '2026-05-20',
        TIMESTAMPTZ '2026-05-20 14:18:00+00', TIMESTAMPTZ '2026-05-20 14:18:09+00',
        'COMPLETED', 1, 12, 12, NULL, 'MANUAL',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'))
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. Leave Requests — full Feb to May 2026 history (15 requests, 4 statuses)
--    IDs use 37aaaaaa- prefix to never collide with prior demo seeds.
-- ----------------------------------------------------------------------------
INSERT INTO leave_requests (
    id, employee_id, leave_type_id, start_date, end_date, working_days,
    duration_days, urgency_level, status, comment, submitted_at, version,
    is_half_day, partial_mode
)
VALUES
    -- February 2026
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000201',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-02-09', DATE '2026-02-13', 5, 5, 'NORMAL', 'APPROVED',
        'Mid-winter family visit',
        TIMESTAMPTZ '2026-02-02 09:14:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000202',
        (SELECT id FROM employees WHERE employee_code = 'EMP-003'),
        (SELECT id FROM leave_types WHERE code = 'SICK'),
        DATE '2026-02-16', DATE '2026-02-17', 2, 2, 'URGENT', 'APPROVED',
        'Flu — medical certificate attached',
        TIMESTAMPTZ '2026-02-16 07:42:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000203',
        (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-02-23', DATE '2026-02-27', 5, 5, 'NORMAL', 'APPROVED',
        'Personal time',
        TIMESTAMPTZ '2026-02-12 11:08:00+00', 0, FALSE, 'FULL_DAY'),
    -- March 2026
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000204',
        (SELECT id FROM employees WHERE employee_code = 'EMP-002'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-03-09', DATE '2026-03-13', 5, 5, 'NORMAL', 'APPROVED',
        'Spring break',
        TIMESTAMPTZ '2026-03-01 10:22:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000205',
        (SELECT id FROM employees WHERE employee_code = 'EMP-QA'),
        (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'),
        DATE '2026-03-16', DATE '2026-03-16', 1, 0.5, 'NORMAL', 'APPROVED',
        'Marriage certificate appointment',
        TIMESTAMPTZ '2026-03-10 16:01:00+00', 0, TRUE, 'HALF_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000206',
        (SELECT id FROM employees WHERE employee_code = 'EMP-004'),
        (SELECT id FROM leave_types WHERE code = 'UNPAID'),
        DATE '2026-03-23', DATE '2026-03-27', 5, 5, 'NORMAL', 'REJECTED',
        'Family event — coverage not feasible',
        TIMESTAMPTZ '2026-03-12 14:30:00+00', 0, FALSE, 'FULL_DAY'),
    -- April 2026
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000207',
        (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-04-06', DATE '2026-04-10', 5, 5, 'NORMAL', 'APPROVED',
        'Easter break',
        TIMESTAMPTZ '2026-03-25 08:50:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000208',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM leave_types WHERE code = 'SICK'),
        DATE '2026-04-13', DATE '2026-04-14', 2, 2, 'URGENT', 'APPROVED',
        'Migraine — medical certificate provided',
        TIMESTAMPTZ '2026-04-13 09:05:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000209',
        (SELECT id FROM employees WHERE employee_code = 'EMP-003'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-04-20', DATE '2026-04-24', 5, 5, 'NORMAL', 'APPROVED',
        'Spring vacation',
        TIMESTAMPTZ '2026-04-02 13:18:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000210',
        (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'),
        (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'),
        DATE '2026-04-27', DATE '2026-04-27', 1, 1, 'URGENT', 'APPROVED',
        'Bereavement leave',
        TIMESTAMPTZ '2026-04-26 17:42:00+00', 0, FALSE, 'FULL_DAY'),
    -- May 2026
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000211',
        (SELECT id FROM employees WHERE employee_code = 'EMP-002'),
        (SELECT id FROM leave_types WHERE code = 'PATERNITY'),
        DATE '2026-05-04', DATE '2026-05-10', 7, 7, 'URGENT', 'APPROVED',
        'Birth of second child',
        TIMESTAMPTZ '2026-04-28 10:00:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000212',
        (SELECT id FROM employees WHERE employee_code = 'EMP-QA'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-05-11', DATE '2026-05-13', 3, 3, 'NORMAL', 'APPROVED',
        'Short break before sprint review',
        TIMESTAMPTZ '2026-05-04 09:33:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000213',
        (SELECT id FROM employees WHERE employee_code = 'EMP-004'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-05-18', DATE '2026-05-22', 5, 5, 'NORMAL', 'IN_APPROVAL',
        'Sousse holiday',
        TIMESTAMPTZ '2026-05-11 14:12:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000214',
        (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'),
        (SELECT id FROM leave_types WHERE code = 'SICK'),
        DATE '2026-05-19', DATE '2026-05-20', 2, 2, 'URGENT', 'APPROVED',
        'Dental procedure',
        TIMESTAMPTZ '2026-05-18 18:50:00+00', 0, FALSE, 'FULL_DAY'),
    ('37aaaaaa-aaaa-aaaa-aaaa-000000000215',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM leave_types WHERE code = 'ANNUAL'),
        DATE '2026-05-25', DATE '2026-05-29', 5, 5, 'NORMAL', 'PENDING',
        'Long weekend — Eid al-Adha bridge',
        TIMESTAMPTZ '2026-05-20 12:00:00+00', 0, FALSE, 'FULL_DAY')
ON CONFLICT (id) DO NOTHING;

-- Approval workflows + steps for the leave requests above
INSERT INTO approval_workflows (
    id, subject_type, subject_id, status, created_at, completed_at, version
)
VALUES
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000201', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000201', 'COMPLETED', TIMESTAMPTZ '2026-02-02 09:14:00+00', TIMESTAMPTZ '2026-02-03 11:00:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000202', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000202', 'COMPLETED', TIMESTAMPTZ '2026-02-16 07:42:00+00', TIMESTAMPTZ '2026-02-16 10:00:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000203', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000203', 'COMPLETED', TIMESTAMPTZ '2026-02-12 11:08:00+00', TIMESTAMPTZ '2026-02-13 12:30:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000204', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000204', 'COMPLETED', TIMESTAMPTZ '2026-03-01 10:22:00+00', TIMESTAMPTZ '2026-03-02 09:45:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000205', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000205', 'COMPLETED', TIMESTAMPTZ '2026-03-10 16:01:00+00', TIMESTAMPTZ '2026-03-11 09:15:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000206', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000206', 'COMPLETED', TIMESTAMPTZ '2026-03-12 14:30:00+00', TIMESTAMPTZ '2026-03-14 10:00:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000207', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000207', 'COMPLETED', TIMESTAMPTZ '2026-03-25 08:50:00+00', TIMESTAMPTZ '2026-03-26 11:10:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000208', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000208', 'COMPLETED', TIMESTAMPTZ '2026-04-13 09:05:00+00', TIMESTAMPTZ '2026-04-13 11:45:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000209', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000209', 'COMPLETED', TIMESTAMPTZ '2026-04-02 13:18:00+00', TIMESTAMPTZ '2026-04-03 09:30:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000210', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000210', 'COMPLETED', TIMESTAMPTZ '2026-04-26 17:42:00+00', TIMESTAMPTZ '2026-04-27 07:15:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000211', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000211', 'COMPLETED', TIMESTAMPTZ '2026-04-28 10:00:00+00', TIMESTAMPTZ '2026-04-29 11:30:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000212', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000212', 'COMPLETED', TIMESTAMPTZ '2026-05-04 09:33:00+00', TIMESTAMPTZ '2026-05-05 10:00:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000213', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000213', 'IN_PROGRESS', TIMESTAMPTZ '2026-05-11 14:12:00+00', NULL, 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000214', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000214', 'COMPLETED', TIMESTAMPTZ '2026-05-18 18:50:00+00', TIMESTAMPTZ '2026-05-19 09:00:00+00', 0),
    ('37bbbbbb-bbbb-bbbb-bbbb-000000000215', 'LEAVE', '37aaaaaa-aaaa-aaaa-aaaa-000000000215', 'IN_PROGRESS', TIMESTAMPTZ '2026-05-20 12:00:00+00', NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (
    id, workflow_id, approver_id, step_order, status, context, source_type,
    approver_level, routing_snapshot, comment, decided_at, version
)
VALUES
    -- Approved chains (single-step manager approval)
    ('37cccccc-cccc-cccc-cccc-000000000201', '37bbbbbb-bbbb-bbbb-bbbb-000000000201', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved — coverage confirmed', TIMESTAMPTZ '2026-02-03 11:00:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000202', '37bbbbbb-bbbb-bbbb-bbbb-000000000202', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Medical certificate received', TIMESTAMPTZ '2026-02-16 10:00:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000203', '37bbbbbb-bbbb-bbbb-bbbb-000000000203', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Approved',                     TIMESTAMPTZ '2026-02-13 12:30:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000204', '37bbbbbb-bbbb-bbbb-bbbb-000000000204', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved',                     TIMESTAMPTZ '2026-03-02 09:45:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000205', '37bbbbbb-bbbb-bbbb-bbbb-000000000205', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved — half day',           TIMESTAMPTZ '2026-03-11 09:15:00+00', 0),
    -- Rejected chain
    ('37cccccc-cccc-cccc-cccc-000000000206', '37bbbbbb-bbbb-bbbb-bbbb-000000000206', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'REJECTED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Unable to cover the period',   TIMESTAMPTZ '2026-03-14 10:00:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000207', '37bbbbbb-bbbb-bbbb-bbbb-000000000207', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Approved',                     TIMESTAMPTZ '2026-03-26 11:10:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000208', '37bbbbbb-bbbb-bbbb-bbbb-000000000208', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved — get well soon',     TIMESTAMPTZ '2026-04-13 11:45:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000209', '37bbbbbb-bbbb-bbbb-bbbb-000000000209', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Approved',                     TIMESTAMPTZ '2026-04-03 09:30:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000210', '37bbbbbb-bbbb-bbbb-bbbb-000000000210', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Condolences — approved',       TIMESTAMPTZ '2026-04-27 07:15:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000211', '37bbbbbb-bbbb-bbbb-bbbb-000000000211', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved — congratulations',   TIMESTAMPTZ '2026-04-29 11:30:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000212', '37bbbbbb-bbbb-bbbb-bbbb-000000000212', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved',                     TIMESTAMPTZ '2026-05-05 10:00:00+00', 0),
    -- In-progress (pending) — leave-request 213
    ('37cccccc-cccc-cccc-cccc-000000000213', '37bbbbbb-bbbb-bbbb-bbbb-000000000213', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'PENDING',  'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', NULL, NULL, 0),
    ('37cccccc-cccc-cccc-cccc-000000000214', '37bbbbbb-bbbb-bbbb-bbbb-000000000214', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"hr_validation"}', 'Medical certificate received', TIMESTAMPTZ '2026-05-19 09:00:00+00', 0),
    -- Two-step pending — leave-request 215
    ('37cccccc-cccc-cccc-cccc-000000000215', '37bbbbbb-bbbb-bbbb-bbbb-000000000215', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 1, 'APPROVED', 'TEAM', 'TEAM_CHAIN', 1, '{"route":"direct_manager"}', 'Approved at level 1',          TIMESTAMPTZ '2026-05-21 09:00:00+00', 0),
    ('37cccccc-cccc-cccc-cccc-000000000216', '37bbbbbb-bbbb-bbbb-bbbb-000000000215', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),             2, 'PENDING',  'TEAM', 'TEAM_CHAIN', 2, '{"route":"hr_validation"}', NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4. Administrative Requests — cover all 7 lifecycle states + multi-month spread
--    Existing seeds: SUBMITTED, IN_REVIEW, APPROVED, REJECTED, COMPLETED.
--    Adds:           DRAFT, CANCELLED, plus extra IN_REVIEW / COMPLETED across months.
-- ----------------------------------------------------------------------------
INSERT INTO admin_requests (
    id, request_number, requester_employee_id, requester_user_id, type_id,
    subject, description, status, submitted_at, due_at, created_at, updated_at,
    processed_by_user_id, reviewed_at, decided_at, completed_at, rejection_reason
)
VALUES
    -- DRAFT — never submitted
    ('37dddddd-dddd-dddd-dddd-000000000301', 'AR-2026-0010',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'),
        'Employment certificate (draft)',
        'Drafting before notarisation appointment is confirmed.',
        'DRAFT',  NULL,                                NULL,
        TIMESTAMPTZ '2026-05-18 09:14:00+00', TIMESTAMPTZ '2026-05-18 09:14:00+00',
        NULL, NULL, NULL, NULL, NULL),
    -- CANCELLED
    ('37dddddd-dddd-dddd-dddd-000000000302', 'AR-2026-0011',
        (SELECT id FROM employees WHERE employee_code = 'EMP-QA'),
        (SELECT id FROM users WHERE email = 'qa@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'EQUIPMENT'),
        'Replacement headset',
        'No longer needed — issue resolved by IT.',
        'CANCELLED', TIMESTAMPTZ '2026-04-22 11:00:00+00', TIMESTAMPTZ '2026-04-29 11:00:00+00',
        TIMESTAMPTZ '2026-04-22 11:00:00+00', TIMESTAMPTZ '2026-04-23 09:00:00+00',
        NULL, NULL, NULL, NULL, NULL),
    -- IN_REVIEW (older, breaching SLA)
    ('37dddddd-dddd-dddd-dddd-000000000303', 'AR-2026-0012',
        (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'),
        (SELECT id FROM users WHERE email = 'legal@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'CERT_SALARY'),
        'Salary attestation for bank loan',
        'Needs gross + net for the past 12 months.',
        'IN_REVIEW', TIMESTAMPTZ '2026-05-12 10:24:00+00', TIMESTAMPTZ '2026-05-15 17:00:00+00',
        TIMESTAMPTZ '2026-05-12 10:24:00+00', TIMESTAMPTZ '2026-05-15 14:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-05-13 09:30:00+00', NULL, NULL, NULL),
    -- APPROVED (awaiting completion)
    ('37dddddd-dddd-dddd-dddd-000000000304', 'AR-2026-0013',
        (SELECT id FROM employees WHERE employee_code = 'EMP-003'),
        (SELECT id FROM users WHERE email = 'office@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'),
        'Visa support letter',
        'For consulate appointment on 2026-06-05.',
        'APPROVED', TIMESTAMPTZ '2026-05-15 14:08:00+00', TIMESTAMPTZ '2026-05-22 17:00:00+00',
        TIMESTAMPTZ '2026-05-15 14:08:00+00', TIMESTAMPTZ '2026-05-20 10:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-05-16 11:00:00+00',
        TIMESTAMPTZ '2026-05-19 16:30:00+00', NULL, NULL),
    -- COMPLETED (early March)
    ('37dddddd-dddd-dddd-dddd-000000000305', 'AR-2026-0014',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'CERT_WORK'),
        'Standard employment certificate',
        'Initial copy for personal records.',
        'COMPLETED', TIMESTAMPTZ '2026-03-04 09:00:00+00', TIMESTAMPTZ '2026-03-11 09:00:00+00',
        TIMESTAMPTZ '2026-03-04 09:00:00+00', TIMESTAMPTZ '2026-03-06 16:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-03-04 11:00:00+00',
        TIMESTAMPTZ '2026-03-05 15:00:00+00',
        TIMESTAMPTZ '2026-03-06 16:00:00+00', NULL),
    -- COMPLETED (April)
    ('37dddddd-dddd-dddd-dddd-000000000306', 'AR-2026-0015',
        (SELECT id FROM employees WHERE employee_code = 'EMP-002'),
        (SELECT id FROM users WHERE email = 'product@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'CERT_SALARY'),
        'Salary attestation — landlord request',
        'Letter for housing rental.',
        'COMPLETED', TIMESTAMPTZ '2026-04-07 10:30:00+00', TIMESTAMPTZ '2026-04-14 10:30:00+00',
        TIMESTAMPTZ '2026-04-07 10:30:00+00', TIMESTAMPTZ '2026-04-10 13:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-04-07 13:00:00+00',
        TIMESTAMPTZ '2026-04-09 09:30:00+00',
        TIMESTAMPTZ '2026-04-10 13:00:00+00', NULL),
    -- REJECTED
    ('37dddddd-dddd-dddd-dddd-000000000307', 'AR-2026-0016',
        (SELECT id FROM employees WHERE employee_code = 'EMP-004'),
        (SELECT id FROM users WHERE email = 'analyst@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'INFO_UPDATE'),
        'Change of address (incomplete)',
        'Form returned without supporting proof-of-address.',
        'REJECTED', TIMESTAMPTZ '2026-03-19 11:18:00+00', TIMESTAMPTZ '2026-03-26 11:18:00+00',
        TIMESTAMPTZ '2026-03-19 11:18:00+00', TIMESTAMPTZ '2026-03-20 10:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-03-19 13:00:00+00',
        TIMESTAMPTZ '2026-03-20 10:00:00+00', NULL,
        'Missing utility bill — please resubmit with proof of address.'),
    -- SUBMITTED — fresh, awaiting HR review
    ('37dddddd-dddd-dddd-dddd-000000000308', 'AR-2026-0017',
        (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'),
        (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'INFO_UPDATE'),
        'Bank IBAN update',
        'Switching to new salary account.',
        'SUBMITTED', TIMESTAMPTZ '2026-05-21 16:00:00+00', TIMESTAMPTZ '2026-05-28 16:00:00+00',
        TIMESTAMPTZ '2026-05-21 16:00:00+00', TIMESTAMPTZ '2026-05-21 16:00:00+00',
        NULL, NULL, NULL, NULL, NULL),
    -- IN_REVIEW (recent, within SLA)
    ('37dddddd-dddd-dddd-dddd-000000000309', 'AR-2026-0018',
        (SELECT id FROM employees WHERE employee_code = 'EMP-001'),
        (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'OTHER'),
        'Training reimbursement (Cloud certification)',
        'AWS Solutions Architect — 285 TND, receipt attached.',
        'IN_REVIEW', TIMESTAMPTZ '2026-05-19 09:48:00+00', TIMESTAMPTZ '2026-05-26 09:48:00+00',
        TIMESTAMPTZ '2026-05-19 09:48:00+00', TIMESTAMPTZ '2026-05-20 11:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-05-20 11:00:00+00', NULL, NULL, NULL),
    -- COMPLETED (Feb, equipment delivery)
    ('37dddddd-dddd-dddd-dddd-000000000310', 'AR-2026-0019',
        (SELECT id FROM employees WHERE employee_code = 'EMP-QA'),
        (SELECT id FROM users WHERE email = 'qa@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'EQUIPMENT'),
        'External monitor for home office',
        'Required for QA dashboards.',
        'COMPLETED', TIMESTAMPTZ '2026-02-09 08:30:00+00', TIMESTAMPTZ '2026-02-16 08:30:00+00',
        TIMESTAMPTZ '2026-02-09 08:30:00+00', TIMESTAMPTZ '2026-02-14 17:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-02-09 11:30:00+00',
        TIMESTAMPTZ '2026-02-11 10:00:00+00',
        TIMESTAMPTZ '2026-02-14 17:00:00+00', NULL),
    -- COMPLETED (May, info update)
    ('37dddddd-dddd-dddd-dddd-000000000311', 'AR-2026-0020',
        (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'),
        (SELECT id FROM users WHERE email = 'legal@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'INFO_UPDATE'),
        'Emergency contact update',
        'New phone number for next of kin.',
        'COMPLETED', TIMESTAMPTZ '2026-05-06 14:20:00+00', TIMESTAMPTZ '2026-05-13 14:20:00+00',
        TIMESTAMPTZ '2026-05-06 14:20:00+00', TIMESTAMPTZ '2026-05-08 09:30:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-05-06 16:00:00+00',
        TIMESTAMPTZ '2026-05-07 10:00:00+00',
        TIMESTAMPTZ '2026-05-08 09:30:00+00', NULL),
    -- APPROVED (recent, equipment)
    ('37dddddd-dddd-dddd-dddd-000000000312', 'AR-2026-0021',
        (SELECT id FROM employees WHERE employee_code = 'EMP-002'),
        (SELECT id FROM users WHERE email = 'product@demo.hris.local'),
        (SELECT id FROM admin_request_types WHERE code = 'EQUIPMENT'),
        'Standing desk',
        'Ergonomic request — medical recommendation attached.',
        'APPROVED', TIMESTAMPTZ '2026-05-13 09:15:00+00', TIMESTAMPTZ '2026-05-20 09:15:00+00',
        TIMESTAMPTZ '2026-05-13 09:15:00+00', TIMESTAMPTZ '2026-05-17 16:00:00+00',
        (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        TIMESTAMPTZ '2026-05-14 11:00:00+00',
        TIMESTAMPTZ '2026-05-17 16:00:00+00', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- Comments and attachments on admin requests (Request Detail screen)
INSERT INTO admin_request_comments (id, admin_request_id, author_user_id, comment, internal, created_at)
VALUES
    ('37cccccc-adad-adad-adad-000000000401', '37dddddd-dddd-dddd-dddd-000000000303', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    'Waiting on payroll export to compute net for past 12 months.',    TRUE,  TIMESTAMPTZ '2026-05-13 09:30:00+00'),
    ('37cccccc-adad-adad-adad-000000000402', '37dddddd-dddd-dddd-dddd-000000000303', (SELECT id FROM users WHERE email = 'legal@demo.hris.local'),       'Could you confirm the gross figure for March 2026?',              FALSE, TIMESTAMPTZ '2026-05-14 14:50:00+00'),
    ('37cccccc-adad-adad-adad-000000000403', '37dddddd-dddd-dddd-dddd-000000000304', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    'Letter signed by Director.  Will be marked completed once delivered.', FALSE, TIMESTAMPTZ '2026-05-20 10:00:00+00'),
    ('37cccccc-adad-adad-adad-000000000404', '37dddddd-dddd-dddd-dddd-000000000307', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    'Address not supported by attached document.',                     FALSE, TIMESTAMPTZ '2026-03-20 10:00:00+00'),
    ('37cccccc-adad-adad-adad-000000000405', '37dddddd-dddd-dddd-dddd-000000000309', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    'Reimbursement validated by Finance.  Will issue payment Friday.', TRUE,  TIMESTAMPTZ '2026-05-20 11:00:00+00'),
    ('37cccccc-adad-adad-adad-000000000406', '37dddddd-dddd-dddd-dddd-000000000312', (SELECT id FROM users WHERE email = 'product@demo.hris.local'),     'Doctor''s note covers June onwards as well.',                     FALSE, TIMESTAMPTZ '2026-05-14 10:00:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_request_attachments (
    id, admin_request_id, file_name, content_type, size_bytes, storage_path,
    response_document, uploaded_by_user_id, uploaded_at
)
VALUES
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000501', '37dddddd-dddd-dddd-dddd-000000000303', 'salary-attestation-draft.pdf',  'application/pdf', 184000, '/demo/admin/salary-attestation-draft.pdf',  TRUE,  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    TIMESTAMPTZ '2026-05-15 14:00:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000502', '37dddddd-dddd-dddd-dddd-000000000304', 'visa-support-letter.pdf',       'application/pdf', 162000, '/demo/admin/visa-support-letter.pdf',       TRUE,  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    TIMESTAMPTZ '2026-05-19 16:30:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000503', '37dddddd-dddd-dddd-dddd-000000000305', 'employment-certificate.pdf',    'application/pdf', 134000, '/demo/admin/employment-certificate.pdf',    TRUE,  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    TIMESTAMPTZ '2026-03-06 15:30:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000504', '37dddddd-dddd-dddd-dddd-000000000306', 'salary-attestation-letter.pdf', 'application/pdf', 145000, '/demo/admin/salary-attestation-letter.pdf', TRUE,  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    TIMESTAMPTZ '2026-04-10 12:30:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000505', '37dddddd-dddd-dddd-dddd-000000000307', 'address-change-form.pdf',       'application/pdf',  87000, '/demo/admin/address-change-form.pdf',       FALSE, (SELECT id FROM users WHERE email = 'analyst@demo.hris.local'),     TIMESTAMPTZ '2026-03-19 11:18:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000506', '37dddddd-dddd-dddd-dddd-000000000308', 'iban-update-form.pdf',          'application/pdf',  91000, '/demo/admin/iban-update-form.pdf',          FALSE, (SELECT id FROM users WHERE email = 'finance.viewer@demo.hris.local'), TIMESTAMPTZ '2026-05-21 16:00:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000507', '37dddddd-dddd-dddd-dddd-000000000309', 'aws-certification-receipt.pdf', 'application/pdf', 215000, '/demo/admin/aws-certification-receipt.pdf', FALSE, (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),   TIMESTAMPTZ '2026-05-19 09:48:00+00'),
    ('37bdbdbd-bdbd-bdbd-bdbd-000000000508', '37dddddd-dddd-dddd-dddd-000000000310', 'monitor-delivery-note.pdf',     'application/pdf', 109000, '/demo/admin/monitor-delivery-note.pdf',     TRUE,  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),    TIMESTAMPTZ '2026-02-14 16:30:00+00')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5. Notifications — one per event type referenced in the report (Sprint 5)
--    plus a few "completed" notifications across users for the dropdown panel.
--    `type` column is one of: SYSTEM / LEAVE / REQUEST (existing data pattern).
-- ----------------------------------------------------------------------------
INSERT INTO notifications (id, user_id, title, body, link_path, is_read, created_at, type, actor_display_name)
VALUES
    -- leave.submitted (HR / manager perspective)
    ('37eeeeee-eeee-eeee-eeee-000000000601', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'),
        'Leave request submitted',
        'Yasmine Trabelsi submitted an annual leave request for 25–29 May 2026.',
        '/leave/requests/37aaaaaa-aaaa-aaaa-aaaa-000000000215', FALSE,
        TIMESTAMPTZ '2026-05-20 12:00:00+00', 'LEAVE', 'Yasmine Trabelsi'),
    -- leave.approved (employee perspective)
    ('37eeeeee-eeee-eeee-eeee-000000000602', (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
        'Leave request approved',
        'Your sick-leave request for 13–14 April 2026 has been approved.',
        '/leave/my-requests/37aaaaaa-aaaa-aaaa-aaaa-000000000208', TRUE,
        TIMESTAMPTZ '2026-04-13 11:45:00+00', 'LEAVE', 'Karim Jlassi'),
    -- leave.rejected (employee perspective)
    ('37eeeeee-eeee-eeee-eeee-000000000603', (SELECT id FROM users WHERE email = 'analyst@demo.hris.local'),
        'Leave request rejected',
        'Your unpaid leave request for 23–27 March 2026 was rejected by HR.',
        '/leave/my-requests/37aaaaaa-aaaa-aaaa-aaaa-000000000206', TRUE,
        TIMESTAMPTZ '2026-03-14 10:00:00+00', 'LEAVE', 'Sami Khadhraoui'),
    -- admin.submitted (HR perspective)
    ('37eeeeee-eeee-eeee-eeee-000000000604', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        'Administrative request submitted',
        'Mehdi Saadi submitted "Bank IBAN update" — INFO_UPDATE.',
        '/admin/admin-requests/37dddddd-dddd-dddd-dddd-000000000308', FALSE,
        TIMESTAMPTZ '2026-05-21 16:00:00+00', 'REQUEST', 'Mehdi Saadi'),
    -- admin.in-review
    ('37eeeeee-eeee-eeee-eeee-000000000605', (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),
        'Your request is under review',
        'HR is reviewing your training reimbursement request.',
        '/admin-requests/37dddddd-dddd-dddd-dddd-000000000309', FALSE,
        TIMESTAMPTZ '2026-05-20 11:00:00+00', 'REQUEST', 'Sami Khadhraoui'),
    -- admin.approved
    ('37eeeeee-eeee-eeee-eeee-000000000606', (SELECT id FROM users WHERE email = 'product@demo.hris.local'),
        'Administrative request approved',
        'Your standing-desk request has been approved.  Awaiting delivery.',
        '/admin-requests/37dddddd-dddd-dddd-dddd-000000000312', FALSE,
        TIMESTAMPTZ '2026-05-17 16:00:00+00', 'REQUEST', 'Sami Khadhraoui'),
    -- admin.completed
    ('37eeeeee-eeee-eeee-eeee-000000000607', (SELECT id FROM users WHERE email = 'legal@demo.hris.local'),
        'Administrative request completed',
        'Your emergency-contact update has been recorded.',
        '/admin-requests/37dddddd-dddd-dddd-dddd-000000000311', TRUE,
        TIMESTAMPTZ '2026-05-08 09:30:00+00', 'REQUEST', 'Sami Khadhraoui'),
    -- system.accrual.completed
    ('37eeeeee-eeee-eeee-eeee-000000000608', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        'Monthly accrual completed',
        'May 2026 accrual run processed 12 employees and credited 12 transactions.',
        '/admin/leave/accrual-runs', TRUE,
        TIMESTAMPTZ '2026-05-01 02:01:00+00', 'SYSTEM', NULL),
    -- system.sla.breach
    ('37eeeeee-eeee-eeee-eeee-000000000609', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),
        'SLA breach on admin request',
        'AR-2026-0012 (Salary attestation) is past its SLA deadline.',
        '/admin/admin-requests/37dddddd-dddd-dddd-dddd-000000000303', FALSE,
        TIMESTAMPTZ '2026-05-15 17:05:00+00', 'SYSTEM', NULL),
    -- system.dashboard (executive)
    ('37eeeeee-eeee-eeee-eeee-000000000610', (SELECT id FROM users WHERE email = 'director@demo.hris.local'),
        'Weekly executive dashboard refreshed',
        'Headcount, leave, and approval metrics for week 21 (May 2026) are ready.',
        '/dashboard', FALSE,
        TIMESTAMPTZ '2026-05-22 06:30:00+00', 'SYSTEM', NULL)
ON CONFLICT (id) DO NOTHING;

-- Notification outbox events (mirroring the report's RabbitMQ routing keys)
INSERT INTO notification_events (
    id, event_type, target_user_id, title_key, body_key, params, locale,
    routing_key, published_at, correlation_id, delivered_at
)
VALUES
    ('37eeeefe-eeee-eeee-eeee-000000000701', 'LEAVE_SUBMITTED',  (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'notifications.leave.submitted.title', 'notifications.leave.submitted.body', '{"requester":"Yasmine Trabelsi","start":"2026-05-25","end":"2026-05-29"}', 'en', 'leave.submitted', TIMESTAMPTZ '2026-05-20 12:00:01+00', 'aabbccdd-aabb-aabb-aabb-000000000701', TIMESTAMPTZ '2026-05-20 12:00:02+00'),
    ('37eeeefe-eeee-eeee-eeee-000000000702', 'LEAVE_APPROVED',   (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),           'notifications.leave.approved.title',  'notifications.leave.approved.body',  '{"requester":"Yasmine Trabelsi","start":"2026-04-13","end":"2026-04-14"}', 'en', 'leave.approved',  TIMESTAMPTZ '2026-04-13 11:45:01+00', 'aabbccdd-aabb-aabb-aabb-000000000702', TIMESTAMPTZ '2026-04-13 11:45:02+00'),
    ('37eeeefe-eeee-eeee-eeee-000000000703', 'ADMIN_SUBMITTED',  (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),            'notifications.admin.submitted.title', 'notifications.admin.submitted.body', '{"requester":"Mehdi Saadi","subject":"Bank IBAN update"}',                    'en', 'admin.submitted', TIMESTAMPTZ '2026-05-21 16:00:01+00', 'aabbccdd-aabb-aabb-aabb-000000000703', TIMESTAMPTZ '2026-05-21 16:00:02+00'),
    ('37eeeefe-eeee-eeee-eeee-000000000704', 'ADMIN_APPROVED',   (SELECT id FROM users WHERE email = 'product@demo.hris.local'),             'notifications.admin.approved.title',  'notifications.admin.approved.body',  '{"subject":"Standing desk"}',                                                 'en', 'admin.approved',  TIMESTAMPTZ '2026-05-17 16:00:01+00', 'aabbccdd-aabb-aabb-aabb-000000000704', TIMESTAMPTZ '2026-05-17 16:00:02+00'),
    ('37eeeefe-eeee-eeee-eeee-000000000705', 'ADMIN_COMPLETED',  (SELECT id FROM users WHERE email = 'legal@demo.hris.local'),               'notifications.admin.completed.title', 'notifications.admin.completed.body', '{"subject":"Emergency contact update"}',                                      'en', 'admin.completed', TIMESTAMPTZ '2026-05-08 09:30:01+00', 'aabbccdd-aabb-aabb-aabb-000000000705', TIMESTAMPTZ '2026-05-08 09:30:02+00'),
    ('37eeeefe-eeee-eeee-eeee-000000000706', 'SYSTEM_ACCRUAL',   (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),            'notifications.system.accrual.title',  'notifications.system.accrual.body',  '{"period":"2026-05","processed":12}',                                         'en', 'system.accrual',  TIMESTAMPTZ '2026-05-01 02:01:01+00', 'aabbccdd-aabb-aabb-aabb-000000000706', TIMESTAMPTZ '2026-05-01 02:01:02+00')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. Audit Logs — full spread across the four risk levels (LOW/MEDIUM/HIGH/CRITICAL)
-- ----------------------------------------------------------------------------
INSERT INTO audit_logs (
    id, actor_id, action, resource, resource_id, previous_state, new_state,
    ip_address, timestamp, actor_type, risk_level
)
VALUES
    -- LOW: routine reads / dashboards
    ('37171717-1717-1717-1717-000000000801', (SELECT id FROM users WHERE email = 'developer@demo.hris.local'),           'READ',   'leave_balance',  '99999999-9999-9999-9999-999999999402', NULL,                                                        '{"availableDays":18}',                                          '10.0.4.21',  TIMESTAMPTZ '2026-05-20 09:14:00+00', 'USER',   'LOW'),
    ('37171717-1717-1717-1717-000000000802', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'READ',   'analytics_dashboard', NULL,                              NULL,                                                        '{"scope":"DEPARTMENT","departmentCode":"ENG"}',                  '10.0.4.5',   TIMESTAMPTZ '2026-05-19 17:35:00+00', 'USER',   'LOW'),
    -- MEDIUM: data modifications (state transitions)
    ('37171717-1717-1717-1717-000000000803', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),            'UPDATE', 'admin_request',  '37dddddd-dddd-dddd-dddd-000000000303', '{"status":"SUBMITTED"}',                                    '{"status":"IN_REVIEW"}',                                        '10.0.4.18',  TIMESTAMPTZ '2026-05-15 14:00:00+00', 'USER',   'MEDIUM'),
    ('37171717-1717-1717-1717-000000000804', (SELECT id FROM users WHERE email = 'manager.engineering@demo.hris.local'), 'APPROVE','approval_step',  '37cccccc-cccc-cccc-cccc-000000000215', '{"status":"PENDING"}',                                      '{"status":"APPROVED"}',                                         '10.0.4.5',   TIMESTAMPTZ '2026-05-21 09:00:00+00', 'USER',   'MEDIUM'),
    ('37171717-1717-1717-1717-000000000805', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),            'UPDATE', 'leave_balance',  '99999999-9999-9999-9999-999999999407', '{"totalDays":23,"usedDays":6}',                             '{"totalDays":24,"usedDays":6}',                                 '10.0.4.18',  TIMESTAMPTZ '2026-05-01 02:00:30+00', 'USER',   'MEDIUM'),
    ('37171717-1717-1717-1717-000000000806', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'),            'CREATE', 'admin_request',  '37dddddd-dddd-dddd-dddd-000000000305', NULL,                                                        '{"status":"COMPLETED","subject":"Standard employment certificate"}', '10.0.4.18',  TIMESTAMPTZ '2026-03-06 16:00:00+00', 'USER',   'MEDIUM'),
    -- HIGH: profile / permission assignments, access scope changes
    ('37171717-1717-1717-1717-000000000807', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),               'ASSIGN', 'user_profile_assignment', NULL,                                  '{"profileCode":"SELF_SERVICE"}',                            '{"profileCode":"HR_CONSOLE"}',                                  '10.0.4.1',   TIMESTAMPTZ '2026-02-04 11:25:00+00', 'USER',   'HIGH'),
    ('37171717-1717-1717-1717-000000000808', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),               'UPDATE', 'access_profile',  (SELECT id FROM access_profiles WHERE code = 'HR_CONSOLE'), '{"permissionCount":18}',                                '{"permissionCount":22}',                                        '10.0.4.1',   TIMESTAMPTZ '2026-03-18 10:42:00+00', 'USER',   'HIGH'),
    ('37171717-1717-1717-1717-000000000809', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),               'ASSIGN', 'user_profile_assignment', NULL,                                  NULL,                                                        '{"user":"director@demo.hris.local","profileCode":"ADMIN_CONSOLE"}', '10.0.4.1',   TIMESTAMPTZ '2026-02-09 09:00:00+00', 'USER',   'HIGH'),
    -- CRITICAL: super-admin / failed authentication / mass exports
    ('37171717-1717-1717-1717-000000000810', NULL,                                                                       'LOGIN_FAIL', 'authentication', NULL,                              '{"reason":"INVALID_CREDENTIALS","attempts":3}',             '{"locked":true}',                                               '85.214.36.7',TIMESTAMPTZ '2026-03-08 04:17:00+00', 'SYSTEM', 'CRITICAL'),
    ('37171717-1717-1717-1717-000000000811', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),               'EXPORT', 'audit_log',      NULL,                                   '{"rows":480,"format":"CSV","range":"2026-01-01..2026-05-21"}', '{"status":"DELIVERED"}',                                       '10.0.4.1',   TIMESTAMPTZ '2026-05-21 18:00:00+00', 'USER',   'CRITICAL'),
    ('37171717-1717-1717-1717-000000000812', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),               'DELETE', 'user',           NULL,                                   '{"email":"contractor@demo.hris.local","isActive":true}',    '{"isActive":false}',                                            '10.0.4.1',   TIMESTAMPTZ '2026-04-30 16:15:00+00', 'USER',   'CRITICAL')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7. Analytics snapshots — populate Feb-May 2026 so trend charts have shape
-- ----------------------------------------------------------------------------
INSERT INTO analytics_leave_metrics_snapshots (
    id, snapshot_date, scope_type, scope_id,
    total_requests, approved_count, rejected_count, pending_count, average_processing_days
)
VALUES
    -- GLOBAL monthly
    ('37242424-2424-2424-2424-000000000901', DATE '2026-02-28', 'GLOBAL', NULL, 3, 3, 0, 0, 1.20),
    ('37242424-2424-2424-2424-000000000902', DATE '2026-03-31', 'GLOBAL', NULL, 4, 3, 1, 0, 1.60),
    ('37242424-2424-2424-2424-000000000903', DATE '2026-04-30', 'GLOBAL', NULL, 4, 4, 0, 0, 1.10),
    ('37242424-2424-2424-2424-000000000904', DATE '2026-05-22', 'GLOBAL', NULL, 5, 3, 0, 2, 1.30),
    -- ENG department monthly
    ('37242424-2424-2424-2424-000000000905', DATE '2026-02-28', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 1, 1, 0, 0, 1.00),
    ('37242424-2424-2424-2424-000000000906', DATE '2026-03-31', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 2, 2, 0, 0, 1.30),
    ('37242424-2424-2424-2424-000000000907', DATE '2026-04-30', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 1, 1, 0, 0, 0.90),
    ('37242424-2424-2424-2424-000000000908', DATE '2026-05-22', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 3, 2, 0, 1, 1.10),
    -- LEGAL department monthly
    ('37242424-2424-2424-2424-000000000909', DATE '2026-04-30', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'LEGAL'), 1, 1, 0, 0, 1.20),
    ('37242424-2424-2424-2424-000000000910', DATE '2026-05-22', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'LEGAL'), 1, 1, 0, 0, 0.80)
ON CONFLICT (snapshot_date, scope_type, scope_id) DO NOTHING;

INSERT INTO analytics_headcount_metrics_snapshots (
    id, snapshot_date, scope_type, scope_id,
    total_employees, active_employees, new_hires, terminated_employees
)
VALUES
    ('37252525-2525-2525-2525-000000000901', DATE '2026-02-28', 'GLOBAL', NULL, 12, 12, 0, 0),
    ('37252525-2525-2525-2525-000000000902', DATE '2026-03-31', 'GLOBAL', NULL, 12, 12, 0, 0),
    ('37252525-2525-2525-2525-000000000903', DATE '2026-04-30', 'GLOBAL', NULL, 12, 12, 0, 0),
    ('37252525-2525-2525-2525-000000000904', DATE '2026-05-22', 'GLOBAL', NULL, 12, 12, 0, 0),
    ('37252525-2525-2525-2525-000000000905', DATE '2026-02-28', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 4, 0, 0),
    ('37252525-2525-2525-2525-000000000906', DATE '2026-03-31', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 4, 0, 0),
    ('37252525-2525-2525-2525-000000000907', DATE '2026-04-30', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 4, 0, 0),
    ('37252525-2525-2525-2525-000000000908', DATE '2026-05-22', 'DEPARTMENT', (SELECT id FROM departments WHERE code = 'ENG'), 4, 4, 0, 0)
ON CONFLICT (snapshot_date, scope_type, scope_id) DO NOTHING;

INSERT INTO analytics_leave_distribution_snapshots (
    id, snapshot_date, scope_type, scope_id, leave_type_id, request_count, total_days
)
VALUES
    ('37262626-2626-2626-2626-000000000901', DATE '2026-05-22', 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'ANNUAL'),      9, 38),
    ('37262626-2626-2626-2626-000000000902', DATE '2026-05-22', 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'SICK'),        4,  8),
    ('37262626-2626-2626-2626-000000000903', DATE '2026-05-22', 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), 2,  2),
    ('37262626-2626-2626-2626-000000000904', DATE '2026-05-22', 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'PATERNITY'),   1,  7),
    ('37262626-2626-2626-2626-000000000905', DATE '2026-05-22', 'GLOBAL', NULL, (SELECT id FROM leave_types WHERE code = 'UNPAID'),      1,  5)
ON CONFLICT (snapshot_date, scope_type, scope_id, leave_type_id) DO NOTHING;

INSERT INTO analytics_approval_bottleneck_snapshots (
    id, snapshot_date, scope_type, scope_id, source_type, approver_level,
    pending_count, average_decision_days, rejection_rate
)
VALUES
    ('37272727-2727-2727-2727-000000000901', DATE '2026-05-22', 'GLOBAL',     NULL,                                          'TEAM_CHAIN', 1, 1, 1.20, 0.07),
    ('37272727-2727-2727-2727-000000000902', DATE '2026-05-22', 'GLOBAL',     NULL,                                          'TEAM_CHAIN', 2, 1, 1.90, 0.00),
    ('37272727-2727-2727-2727-000000000903', DATE '2026-05-22', 'DEPARTMENT', (SELECT id FROM departments WHERE code='ENG'), 'TEAM_CHAIN', 1, 1, 0.80, 0.00),
    ('37272727-2727-2727-2727-000000000904', DATE '2026-05-22', 'DEPARTMENT', (SELECT id FROM departments WHERE code='LEGAL'),'TEAM_CHAIN',1, 0, 0.60, 0.00)
ON CONFLICT (snapshot_date, scope_type, scope_id, source_type, approver_level) DO NOTHING;

-- Leave-fact rows backing the trend (one per non-draft leave request)
INSERT INTO analytics_leave_facts (
    id, event_date, leave_request_id, employee_id, department_id, project_id, team_id,
    leave_type_id, working_days, request_status, approval_duration_days
)
VALUES
    ('37202020-2020-2020-2020-000001000001', DATE '2026-02-09', '37aaaaaa-aaaa-aaaa-aaaa-000000000201', (SELECT id FROM employees WHERE employee_code='EMP-001'),   (SELECT id FROM departments WHERE code='ENG'),  (SELECT id FROM projects WHERE code='P-ATLAS'),  (SELECT id FROM teams WHERE code='ENG_PLATFORM'),     (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000002', DATE '2026-02-16', '37aaaaaa-aaaa-aaaa-aaaa-000000000202', (SELECT id FROM employees WHERE employee_code='EMP-003'),   (SELECT id FROM departments WHERE code='OPS'),  (SELECT id FROM projects WHERE code='P-BRAND26'),(SELECT id FROM teams WHERE code='OPS_SUPPORT'),      (SELECT id FROM leave_types WHERE code='SICK'),        2, 'APPROVED', 0),
    ('37202020-2020-2020-2020-000001000003', DATE '2026-02-23', '37aaaaaa-aaaa-aaaa-aaaa-000000000203', (SELECT id FROM employees WHERE employee_code='EMP-FINV'),  (SELECT id FROM departments WHERE code='FIN'),  NULL,                                            NULL,                                                  (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000004', DATE '2026-03-09', '37aaaaaa-aaaa-aaaa-aaaa-000000000204', (SELECT id FROM employees WHERE employee_code='EMP-002'),   (SELECT id FROM departments WHERE code='IT'),   (SELECT id FROM projects WHERE code='P-MOBILE3'),(SELECT id FROM teams WHERE code='PROD_CORE'),        (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000005', DATE '2026-03-16', '37aaaaaa-aaaa-aaaa-aaaa-000000000205', (SELECT id FROM employees WHERE employee_code='EMP-QA'),    (SELECT id FROM departments WHERE code='ENG'),  (SELECT id FROM projects WHERE code='P-ATLAS'),  (SELECT id FROM teams WHERE code='ENG_PLATFORM'),     (SELECT id FROM leave_types WHERE code='EXCEPTIONAL'), 1, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000006', DATE '2026-03-23', '37aaaaaa-aaaa-aaaa-aaaa-000000000206', (SELECT id FROM employees WHERE employee_code='EMP-004'),   (SELECT id FROM departments WHERE code='FIN'),  NULL,                                            NULL,                                                  (SELECT id FROM leave_types WHERE code='UNPAID'),      5, 'REJECTED', 2),
    ('37202020-2020-2020-2020-000001000007', DATE '2026-04-06', '37aaaaaa-aaaa-aaaa-aaaa-000000000207', (SELECT id FROM employees WHERE employee_code='EMP-LEGAL'), (SELECT id FROM departments WHERE code='LEGAL'),(SELECT id FROM projects WHERE code='P-ISO27001'),(SELECT id FROM teams WHERE code='LEGAL_COMPLIANCE'), (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000008', DATE '2026-04-13', '37aaaaaa-aaaa-aaaa-aaaa-000000000208', (SELECT id FROM employees WHERE employee_code='EMP-001'),   (SELECT id FROM departments WHERE code='ENG'),  (SELECT id FROM projects WHERE code='P-ATLAS'),  (SELECT id FROM teams WHERE code='ENG_PLATFORM'),     (SELECT id FROM leave_types WHERE code='SICK'),        2, 'APPROVED', 0),
    ('37202020-2020-2020-2020-000001000009', DATE '2026-04-20', '37aaaaaa-aaaa-aaaa-aaaa-000000000209', (SELECT id FROM employees WHERE employee_code='EMP-003'),   (SELECT id FROM departments WHERE code='OPS'),  (SELECT id FROM projects WHERE code='P-BRAND26'),(SELECT id FROM teams WHERE code='OPS_SUPPORT'),      (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000010', DATE '2026-04-27', '37aaaaaa-aaaa-aaaa-aaaa-000000000210', (SELECT id FROM employees WHERE employee_code='EMP-FINV'),  (SELECT id FROM departments WHERE code='FIN'),  NULL,                                            NULL,                                                  (SELECT id FROM leave_types WHERE code='EXCEPTIONAL'), 1, 'APPROVED', 0),
    ('37202020-2020-2020-2020-000001000011', DATE '2026-05-04', '37aaaaaa-aaaa-aaaa-aaaa-000000000211', (SELECT id FROM employees WHERE employee_code='EMP-002'),   (SELECT id FROM departments WHERE code='IT'),   (SELECT id FROM projects WHERE code='P-MOBILE3'),(SELECT id FROM teams WHERE code='PROD_CORE'),        (SELECT id FROM leave_types WHERE code='PATERNITY'),   7, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000012', DATE '2026-05-11', '37aaaaaa-aaaa-aaaa-aaaa-000000000212', (SELECT id FROM employees WHERE employee_code='EMP-QA'),    (SELECT id FROM departments WHERE code='ENG'),  (SELECT id FROM projects WHERE code='P-ATLAS'),  (SELECT id FROM teams WHERE code='ENG_PLATFORM'),     (SELECT id FROM leave_types WHERE code='ANNUAL'),      3, 'APPROVED', 1),
    ('37202020-2020-2020-2020-000001000013', DATE '2026-05-18', '37aaaaaa-aaaa-aaaa-aaaa-000000000213', (SELECT id FROM employees WHERE employee_code='EMP-004'),   (SELECT id FROM departments WHERE code='FIN'),  NULL,                                            NULL,                                                  (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'IN_APPROVAL', 0),
    ('37202020-2020-2020-2020-000001000014', DATE '2026-05-19', '37aaaaaa-aaaa-aaaa-aaaa-000000000214', (SELECT id FROM employees WHERE employee_code='EMP-LEGAL'), (SELECT id FROM departments WHERE code='LEGAL'),(SELECT id FROM projects WHERE code='P-ISO27001'),(SELECT id FROM teams WHERE code='LEGAL_COMPLIANCE'), (SELECT id FROM leave_types WHERE code='SICK'),        2, 'APPROVED', 0),
    ('37202020-2020-2020-2020-000001000015', DATE '2026-05-25', '37aaaaaa-aaaa-aaaa-aaaa-000000000215', (SELECT id FROM employees WHERE employee_code='EMP-001'),   (SELECT id FROM departments WHERE code='ENG'),  (SELECT id FROM projects WHERE code='P-ATLAS'),  (SELECT id FROM teams WHERE code='ENG_PLATFORM'),     (SELECT id FROM leave_types WHERE code='ANNUAL'),      5, 'PENDING',     0)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 8. Leave balance updates so the My Leaves screen reconciles with the new requests
-- ----------------------------------------------------------------------------
UPDATE leave_balances
SET used_days   = 8,
    pending_days = 5,
    version     = version + 1
WHERE employee_id = (SELECT id FROM employees WHERE employee_code = 'EMP-001')
  AND leave_type_id = (SELECT id FROM leave_types WHERE code = 'ANNUAL')
  AND year = 2026;

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('37999999-9999-9999-9999-000000001001', (SELECT id FROM employees WHERE employee_code = 'EMP-001'),   (SELECT id FROM leave_types WHERE code = 'SICK'),         2026, 15, 2, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001002', (SELECT id FROM employees WHERE employee_code = 'EMP-003'),   (SELECT id FROM leave_types WHERE code = 'ANNUAL'),       2026, 24, 5, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001003', (SELECT id FROM employees WHERE employee_code = 'EMP-003'),   (SELECT id FROM leave_types WHERE code = 'SICK'),         2026, 15, 2, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001004', (SELECT id FROM employees WHERE employee_code = 'EMP-002'),   (SELECT id FROM leave_types WHERE code = 'ANNUAL'),       2026, 24, 5, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001005', (SELECT id FROM employees WHERE employee_code = 'EMP-002'),   (SELECT id FROM leave_types WHERE code = 'PATERNITY'),    2026,  7, 7, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001006', (SELECT id FROM employees WHERE employee_code = 'EMP-004'),   (SELECT id FROM leave_types WHERE code = 'ANNUAL'),       2026, 24, 0, 5, 0, 0),
    ('37999999-9999-9999-9999-000000001007', (SELECT id FROM employees WHERE employee_code = 'EMP-004'),   (SELECT id FROM leave_types WHERE code = 'UNPAID'),       2026,  0, 0, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001008', (SELECT id FROM employees WHERE employee_code = 'EMP-LEGAL'), (SELECT id FROM leave_types WHERE code = 'SICK'),         2026, 15, 2, 0, 0, 0),
    ('37999999-9999-9999-9999-000000001009', (SELECT id FROM employees WHERE employee_code = 'EMP-FINV'),  (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'),  2026,  5, 1, 0, 0, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO NOTHING;

COMMIT;
