-- =============================================================================
-- V71: Employee documents — typed vault with expiry alerts
-- =============================================================================
-- See docs/DOCUMENTS_DESIGN.md. Both employees (own vault) and HR upload; no
-- verification workflow. Document type is a Java enum (no config table, so the
-- tenant-provisioning template clone list stays untouched). Profile grants join
-- through access_profiles so every existing tenant receives them.

-- -----------------------------------------------------------------------------
-- 1. Table
-- -----------------------------------------------------------------------------

CREATE TABLE employee_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    doc_type            VARCHAR(30) NOT NULL,
    title               VARCHAR(150) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(100) NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    issue_date          DATE,
    expiry_date         DATE,
    note                VARCHAR(500),
    uploaded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    expiry_notified_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id           UUID NOT NULL
        DEFAULT NULLIF(current_setting('app.current_tenant', true), '')::uuid
        REFERENCES tenants(id),
    CONSTRAINT ck_employee_documents_type CHECK (doc_type IN
        ('ID_CARD', 'PASSPORT', 'WORK_PERMIT', 'DIPLOMA', 'CONTRACT',
         'CERTIFICATE', 'RIB', 'MEDICAL', 'OTHER')),
    CONSTRAINT ck_employee_documents_dates CHECK
        (issue_date IS NULL OR expiry_date IS NULL OR expiry_date >= issue_date)
);

CREATE INDEX idx_employee_documents_employee ON employee_documents (employee_id);
CREATE INDEX idx_employee_documents_expiry ON employee_documents (expiry_date)
    WHERE expiry_date IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. RLS (V66/V67 idiom)
-- -----------------------------------------------------------------------------

ALTER TABLE employee_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON employee_documents
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------------
-- 3. Permissions (global catalog) + per-tenant grants
--    NOT granted to approver profiles: personal documents are HR-sensitive.
-- -----------------------------------------------------------------------------

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555994', 'DOCUMENT_MANAGE_OWN', 'DOCUMENT', 'MANAGE_OWN',
        'OWN', 'Upload, download and delete own documents', TRUE),
    ('55555555-5555-5555-5555-555555555995', 'DOCUMENT_READ', 'DOCUMENT', 'READ',
        'SCOPED', 'View and download documents of employees in scope', TRUE),
    ('55555555-5555-5555-5555-555555555996', 'DOCUMENT_MANAGE', 'DOCUMENT', 'MANAGE',
        'SCOPED', 'Upload and delete documents of employees in scope', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name = 'DOCUMENT_MANAGE_OWN'
WHERE p.code IN ('SELF_SERVICE', 'MANAGER_INBOX', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, perm.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN permissions perm ON perm.name IN ('DOCUMENT_READ', 'DOCUMENT_MANAGE')
WHERE p.code IN ('HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, permission_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Menu item (global catalog) + per-tenant menu grants
-- -----------------------------------------------------------------------------

INSERT INTO menu_items (id, code, translation_key, section_code, route, icon, display_order, is_active)
VALUES
    ('99999999-9999-9999-9999-999999999942', 'menu.workspace.myDocuments',
        'menu.workspace.myDocuments', 'WORKSPACE', '/documents', 'document-text', 36, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_menu_access (profile_id, menu_item_id, granted_at, granted_by_id, tenant_id)
SELECT p.id, m.id, NOW(), NULL, p.tenant_id
FROM access_profiles p
JOIN menu_items m ON m.code = 'menu.workspace.myDocuments'
WHERE p.code IN ('SELF_SERVICE', 'MANAGER_INBOX', 'HR_CONSOLE', 'ADMIN_CONSOLE')
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
