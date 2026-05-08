-- Optional manual seed.
-- The Flyway baseline already inserts the same core reference data.
-- Re-run only on environments where you intentionally want to realign reference rows.

BEGIN;

INSERT INTO work_schedules (id, name, working_days, hours_per_day)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'Standard 40h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 8),
    ('11111111-1111-1111-1111-111111111102', 'Support 35h', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 7)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    working_days = EXCLUDED.working_days,
    hours_per_day = EXCLUDED.hours_per_day;

INSERT INTO public_holidays (id, date, name, is_recurring)
VALUES
    ('11111111-1111-1111-1111-111111111201', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 1, 1), 'New Year''s Day', TRUE),
    ('11111111-1111-1111-1111-111111111202', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 1), 'Labour Day', TRUE),
    ('11111111-1111-1111-1111-111111111203', make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int, 12, 17), 'Revolution Day', TRUE)
ON CONFLICT (id) DO UPDATE
SET date = EXCLUDED.date,
    name = EXCLUDED.name,
    is_recurring = EXCLUDED.is_recurring;

INSERT INTO leave_types (id, code, name, is_paid, requires_justification, is_active)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'ANNUAL', 'Annual Leave', TRUE, FALSE, TRUE),
    ('22222222-2222-2222-2222-222222222202', 'SICK', 'Sick Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222203', 'MATERNITY', 'Maternity Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222204', 'PATERNITY', 'Paternity Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222205', 'EXCEPTIONAL', 'Exceptional Leave', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222206', 'UNPAID', 'Unpaid Leave', FALSE, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_paid = EXCLUDED.is_paid,
    requires_justification = EXCLUDED.requires_justification,
    is_active = EXCLUDED.is_active;

INSERT INTO admin_request_types (id, code, name, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333201', 'CERT_WORK', 'Work Certificate', TRUE),
    ('33333333-3333-3333-3333-333333333202', 'CERT_SALARY', 'Salary Certificate', TRUE),
    ('33333333-3333-3333-3333-333333333203', 'INFO_UPDATE', 'Personal Information Update', TRUE),
    ('33333333-3333-3333-3333-333333333204', 'EQUIPMENT', 'Equipment Request', TRUE),
    ('33333333-3333-3333-3333-333333333205', 'OTHER', 'Other', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

COMMIT;
