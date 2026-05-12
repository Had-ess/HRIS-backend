ALTER TABLE admin_request_types
    ADD COLUMN description TEXT,
    ADD COLUMN requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sla_hours INTEGER,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE admin_request_types
    ADD CONSTRAINT chk_admin_request_types_sla_hours_positive
        CHECK (sla_hours IS NULL OR sla_hours > 0);

UPDATE admin_request_types
SET description = CASE code
    WHEN 'CERT_WORK' THEN 'Request an employment certificate.'
    WHEN 'CERT_SALARY' THEN 'Request a salary certificate.'
    WHEN 'INFO_UPDATE' THEN 'Request an update to personal information.'
    WHEN 'EQUIPMENT' THEN 'Request equipment or office supplies.'
    WHEN 'OTHER' THEN 'General administrative service request.'
    ELSE COALESCE(description, name)
END,
    requires_attachment = CASE code
        WHEN 'INFO_UPDATE' THEN TRUE
        ELSE FALSE
    END,
    sla_hours = CASE code
        WHEN 'CERT_WORK' THEN 24
        WHEN 'CERT_SALARY' THEN 24
        WHEN 'INFO_UPDATE' THEN 72
        WHEN 'EQUIPMENT' THEN 120
        ELSE 72
    END,
    updated_at = NOW()
WHERE description IS NULL
   OR sla_hours IS NULL;

ALTER TABLE admin_requests
    RENAME COLUMN requester_id TO requester_user_id;

ALTER TABLE admin_requests
    RENAME COLUMN request_type_id TO type_id;

ALTER TABLE admin_requests
    RENAME COLUMN tracking_number TO request_number;

ALTER TABLE admin_requests
    RENAME COLUMN resolved_by_id TO processed_by_user_id;

ALTER TABLE admin_requests
    ADD COLUMN requester_employee_id UUID,
    ADD COLUMN subject VARCHAR(255),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE admin_requests ar
SET requester_employee_id = e.id
FROM employees e
WHERE e.user_id = ar.requester_user_id
  AND ar.requester_employee_id IS NULL;

UPDATE admin_requests ar
SET subject = COALESCE(NULLIF(BTRIM(art.name), ''), 'Administrative Request')
FROM admin_request_types art
WHERE art.id = ar.type_id
  AND ar.subject IS NULL;

UPDATE admin_requests ar
SET status = CASE ar.status
        WHEN 'IN_PROGRESS' THEN 'IN_REVIEW'
        WHEN 'PROCESSED' THEN 'COMPLETED'
        ELSE ar.status
    END,
    reviewed_at = CASE
        WHEN ar.status = 'IN_PROGRESS' THEN COALESCE(ar.submitted_at, NOW())
        ELSE ar.reviewed_at
    END,
    decided_at = CASE
        WHEN ar.status IN ('PROCESSED', 'REJECTED') THEN COALESCE(ar.resolved_at, NOW())
        ELSE ar.decided_at
    END,
    completed_at = CASE
        WHEN ar.status = 'PROCESSED' THEN COALESCE(ar.resolved_at, NOW())
        ELSE ar.completed_at
    END,
    due_at = CASE
        WHEN art.sla_hours IS NOT NULL AND ar.submitted_at IS NOT NULL
            THEN ar.submitted_at + make_interval(hours => art.sla_hours)
        ELSE NULL
    END,
    created_at = COALESCE(ar.submitted_at, NOW()),
    updated_at = NOW()
FROM admin_request_types art
WHERE art.id = ar.type_id;

ALTER TABLE admin_requests
    ALTER COLUMN requester_employee_id SET NOT NULL,
    ALTER COLUMN subject SET NOT NULL,
    ALTER COLUMN submitted_at DROP NOT NULL;

ALTER TABLE admin_requests
    ADD CONSTRAINT fk_admin_requests_requester_employee_id
        FOREIGN KEY (requester_employee_id) REFERENCES employees(id);

ALTER TABLE admin_requests
    DROP CONSTRAINT uq_admin_requests_tracking_number;

ALTER TABLE admin_requests
    ADD CONSTRAINT uq_admin_requests_request_number UNIQUE (request_number);

ALTER TABLE admin_requests
    DROP CONSTRAINT fk_admin_requests_request_type_id;

ALTER TABLE admin_requests
    ADD CONSTRAINT fk_admin_requests_type_id
        FOREIGN KEY (type_id) REFERENCES admin_request_types(id);

ALTER TABLE admin_requests
    DROP CONSTRAINT fk_admin_requests_resolved_by_id;

ALTER TABLE admin_requests
    ADD CONSTRAINT fk_admin_requests_processed_by_user_id
        FOREIGN KEY (processed_by_user_id) REFERENCES users(id);

ALTER TABLE admin_requests
    DROP COLUMN urgency_level,
    DROP COLUMN metadata,
    DROP COLUMN resolved_at;

ALTER TABLE admin_requests
    RENAME CONSTRAINT fk_admin_requests_requester_id TO fk_admin_requests_requester_user_id;

ALTER TABLE admin_requests
    ADD CONSTRAINT chk_admin_requests_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'));

DROP INDEX IF EXISTS idx_admin_requests_requester_id;
CREATE INDEX idx_admin_requests_requester_user_id ON admin_requests(requester_user_id);
CREATE INDEX idx_admin_requests_requester_employee_id ON admin_requests(requester_employee_id);
CREATE INDEX idx_admin_requests_type_id ON admin_requests(type_id);
CREATE INDEX idx_admin_requests_due_at ON admin_requests(due_at);

CREATE TABLE admin_request_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_request_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    response_document BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by_user_id UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_admin_request_attachments_request
        FOREIGN KEY (admin_request_id) REFERENCES admin_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_request_attachments_uploaded_by
        FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_admin_request_attachments_request_id
    ON admin_request_attachments(admin_request_id);

CREATE TABLE admin_request_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_request_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    comment TEXT NOT NULL,
    internal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_admin_request_comments_request
        FOREIGN KEY (admin_request_id) REFERENCES admin_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_request_comments_author
        FOREIGN KEY (author_user_id) REFERENCES users(id)
);

CREATE INDEX idx_admin_request_comments_request_id
    ON admin_request_comments(admin_request_id);

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555201', 'ADMIN_REQUEST_CREATE', 'ADMIN_REQUEST', 'CREATE', 'OWN', 'Create administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555202', 'ADMIN_REQUEST_READ_OWN', 'ADMIN_REQUEST', 'READ_OWN', 'OWN', 'Read own administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555203', 'ADMIN_REQUEST_CANCEL_OWN', 'ADMIN_REQUEST', 'CANCEL_OWN', 'OWN', 'Cancel own administration requests', TRUE),
    ('55555555-5555-5555-5555-555555555204', 'ADMIN_REQUEST_READ_GLOBAL', 'ADMIN_REQUEST', 'READ_GLOBAL', 'GLOBAL', 'Read all administration requests', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN ('ADMIN_REQUEST_CREATE', 'ADMIN_REQUEST_READ_OWN', 'ADMIN_REQUEST_CANCEL_OWN')
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN (
      'ADMIN_REQUEST_CREATE',
      'ADMIN_REQUEST_READ_OWN',
      'ADMIN_REQUEST_CANCEL_OWN',
      'ADMIN_REQUEST_INBOX_READ',
      'ADMIN_REQUEST_READ_GLOBAL',
      'ADMIN_REQUEST_PROCESS',
      'ADMIN_REQUEST_APPROVE',
      'ADMIN_REQUEST_REJECT',
      'ADMIN_REQUEST_COMPLETE',
      'ADMIN_REQUEST_TYPE_MANAGE'
  )
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission
  ON permission.name IN (
      'ADMIN_REQUEST_CREATE',
      'ADMIN_REQUEST_READ_OWN',
      'ADMIN_REQUEST_CANCEL_OWN',
      'ADMIN_REQUEST_INBOX_READ',
      'ADMIN_REQUEST_READ_GLOBAL',
      'ADMIN_REQUEST_PROCESS',
      'ADMIN_REQUEST_APPROVE',
      'ADMIN_REQUEST_REJECT',
      'ADMIN_REQUEST_COMPLETE',
      'ADMIN_REQUEST_TYPE_MANAGE'
  )
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;
