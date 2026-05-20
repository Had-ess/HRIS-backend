BEGIN;

INSERT INTO employees (
    id,
    user_id,
    employee_code,
    hire_date,
    job_title,
    status,
    contract_type,
    department_id,
    supervisor_employee_id,
    termination_date,
    work_schedule_id,
    location,
    cin
)
SELECT
    '55555555-5555-5555-5555-555555555412',
    user_record.id,
    'EMP-OPS-SUP',
    CURRENT_DATE - INTERVAL '480 days',
    'Operations Supervisor',
    'ACTIVE',
    'PERMANENT',
    (SELECT id FROM departments WHERE code = 'OPS'),
    (SELECT id FROM employees WHERE employee_code = 'EMP-DIR'),
    NULL,
    (SELECT id FROM work_schedules WHERE name = 'Standard 40h'),
    'Tunis HQ',
    '12000005'
FROM users user_record
WHERE user_record.email = 'supervisor.operations@demo.hris.local'
  AND NOT EXISTS (
      SELECT 1
      FROM employees existing
      WHERE existing.user_id = user_record.id
  );

UPDATE employees
SET department_id = COALESCE(department_id, (SELECT id FROM departments WHERE code = 'OPS')),
    supervisor_employee_id = COALESCE(supervisor_employee_id, (SELECT id FROM employees WHERE employee_code = 'EMP-DIR')),
    work_schedule_id = COALESCE(work_schedule_id, (SELECT id FROM work_schedules WHERE name = 'Standard 40h')),
    job_title = COALESCE(NULLIF(job_title, ''), 'Operations Supervisor'),
    status = COALESCE(status, 'ACTIVE'),
    contract_type = COALESCE(contract_type, 'PERMANENT')
WHERE user_id = (SELECT id FROM users WHERE email = 'supervisor.operations@demo.hris.local');

COMMIT;
