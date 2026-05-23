-- Re-normalize legacy 'LEAD' values reintroduced by V34's demo seed.
-- The Java enum ProjectRole only declares MEMBER and MANAGER; V26 cleaned this up
-- once before, but V34 inserted fresh LEAD rows that fail @Enumerated mapping at startup.
UPDATE project_assignments
SET assignment_role = 'MANAGER'
WHERE assignment_role = 'LEAD';
