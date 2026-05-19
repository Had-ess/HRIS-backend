BEGIN;

UPDATE approval_workflows
SET subject_type = 'LEAVE'
WHERE subject_type = 'LEAVE_REQUEST';

UPDATE approval_steps AS step
SET context = CASE
    WHEN step.source_type = 'TEAM_CHAIN' THEN 'TEAM'
    ELSE 'DEPARTMENT'
END
FROM approval_workflows AS workflow
WHERE workflow.id = step.workflow_id
  AND workflow.subject_type = 'LEAVE'
  AND step.context NOT IN ('DEPARTMENT', 'TEAM');

COMMIT;
