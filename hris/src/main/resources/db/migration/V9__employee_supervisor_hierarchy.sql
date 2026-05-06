ALTER TABLE employees
    ADD COLUMN supervisor_employee_id UUID;

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_supervisor_employee_id
        FOREIGN KEY (supervisor_employee_id) REFERENCES employees(id);

CREATE INDEX idx_employees_supervisor_employee_id ON employees(supervisor_employee_id);

UPDATE employees e
SET supervisor_employee_id = d.head_employee_id
FROM departments d
WHERE e.department_id = d.id
  AND d.head_employee_id IS NOT NULL
  AND d.head_employee_id <> e.id
  AND e.supervisor_employee_id IS NULL;
