BEGIN;

UPDATE leave_types
SET balance_tracked = TRUE
WHERE code = 'UNPAID';

INSERT INTO leave_balances (id, employee_id, leave_type_id, year, total_days, used_days, pending_days, carry_over_days, version)
VALUES
    ('99999999-9999-9999-9999-999999999411', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'SICK'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 15, 1, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999412', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 0, 1, 0, 0),
    ('99999999-9999-9999-9999-999999999413', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'UNPAID'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 0, 0, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999414', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'SICK'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 15, 2, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999415', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 1, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999416', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'UNPAID'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 0, 0, 2, 0, 0),
    ('99999999-9999-9999-9999-999999999417', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'SICK'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 15, 3, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999418', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 5, 0, 1, 0, 0),
    ('99999999-9999-9999-9999-999999999419', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'UNPAID'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 0, 0, 0, 0, 0),
    ('99999999-9999-9999-9999-999999999420', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'PATERNITY'), EXTRACT(YEAR FROM CURRENT_DATE)::int, 7, 0, 0, 0, 0)
ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
SET total_days = EXCLUDED.total_days,
    used_days = EXCLUDED.used_days,
    pending_days = EXCLUDED.pending_days,
    carry_over_days = EXCLUDED.carry_over_days;

INSERT INTO leave_balance_transactions (id, employee_id, leave_type_id, transaction_type, amount, balance_after, source_type, source_id, comment, created_by_user_id, occurred_at)
VALUES
    ('15151515-1515-1515-1515-151515151508', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'SICK'), 'ACCRUAL', 15, 14, 'SYSTEM', NULL, 'Initial sick leave allocation', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '70 days'),
    ('15151515-1515-1515-1515-151515151509', (SELECT id FROM employees WHERE employee_code = 'EMP-ADMIN'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), 'REQUEST_RESERVATION', -1, 4, 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0411', 'Reserved exceptional leave', (SELECT id FROM users WHERE email = 'admin@demo.hris.local'), NOW() - INTERVAL '6 days'),
    ('15151515-1515-1515-1515-151515151510', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'SICK'), 'REQUEST_APPROVAL_CONFIRMATION', -2, 13, 'LEAVE_REQUEST', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0412', 'Approved sick leave used', (SELECT id FROM users WHERE email = 'developer@demo.hris.local'), NOW() - INTERVAL '14 days'),
    ('15151515-1515-1515-1515-151515151511', (SELECT id FROM employees WHERE employee_code = 'EMP-001'), (SELECT id FROM leave_types WHERE code = 'EXCEPTIONAL'), 'MANUAL_ADJUSTMENT', 1, 5, 'MANUAL_ADJUSTMENT', NULL, 'HR manual correction', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '9 days'),
    ('15151515-1515-1515-1515-151515151512', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'SICK'), 'ACCRUAL', 15, 12, 'SYSTEM', NULL, 'Initial sick leave allocation', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '45 days'),
    ('15151515-1515-1515-1515-151515151513', (SELECT id FROM employees WHERE employee_code = 'EMP-002'), (SELECT id FROM leave_types WHERE code = 'PATERNITY'), 'ACCRUAL', 7, 7, 'SYSTEM', NULL, 'Initial paternity leave allocation', (SELECT id FROM users WHERE email = 'hr.admin@demo.hris.local'), NOW() - INTERVAL '5 days')
ON CONFLICT (id) DO NOTHING;

COMMIT;
