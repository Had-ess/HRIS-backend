ALTER TABLE leave_types
    ADD COLUMN IF NOT EXISTS balance_tracked BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE leave_types
SET balance_tracked = CASE
    WHEN code = 'UNPAID' THEN FALSE
    ELSE TRUE
END
WHERE balance_tracked IS DISTINCT FROM CASE
    WHEN code = 'UNPAID' THEN FALSE
    ELSE TRUE
END;

CREATE TABLE hr_calendars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(80),
    timezone VARCHAR(80) NOT NULL,
    hours_per_day INTEGER NOT NULL,
    source VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_calendars_code UNIQUE (code),
    CONSTRAINT chk_hr_calendars_hours_per_day CHECK (hours_per_day > 0 AND hours_per_day <= 24)
);

CREATE INDEX idx_hr_calendars_active ON hr_calendars(is_active);

CREATE TABLE hr_holidays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID NOT NULL,
    date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hr_holidays_calendar_id FOREIGN KEY (calendar_id) REFERENCES hr_calendars(id) ON DELETE CASCADE,
    CONSTRAINT uq_hr_holidays_calendar_date_name UNIQUE (calendar_id, date, name)
);

CREATE INDEX idx_hr_holidays_calendar_date ON hr_holidays(calendar_id, date);

CREATE TABLE enterprise_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton_key BOOLEAN NOT NULL DEFAULT TRUE,
    monthly_acquisition_rate INTEGER,
    max_authorizations_per_month INTEGER,
    max_authorization_hours INTEGER,
    work_week_pattern VARCHAR(50),
    default_validation_workflow_id UUID,
    default_workflow_sla_hours INTEGER,
    default_validation_sla_hours INTEGER,
    active_calendar_id UUID,
    working_hours_per_day INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_enterprise_settings_singleton UNIQUE (singleton_key),
    CONSTRAINT fk_enterprise_settings_default_validation_workflow_id FOREIGN KEY (default_validation_workflow_id) REFERENCES validation_workflows(id),
    CONSTRAINT fk_enterprise_settings_active_calendar_id FOREIGN KEY (active_calendar_id) REFERENCES hr_calendars(id),
    CONSTRAINT chk_enterprise_settings_working_hours CHECK (working_hours_per_day IS NULL OR (working_hours_per_day > 0 AND working_hours_per_day <= 24))
);

CREATE TABLE leave_acquisition_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    leave_type_id UUID NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    monthly_rate INTEGER,
    annual_quota INTEGER,
    day_cap INTEGER,
    acquisition_day INTEGER,
    prorata_hire BOOLEAN NOT NULL DEFAULT FALSE,
    negative_balance_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_leave_acquisition_policies_code UNIQUE (code),
    CONSTRAINT fk_leave_acquisition_policies_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id),
    CONSTRAINT chk_leave_acquisition_policies_date_range CHECK (end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_leave_acquisition_policies_acquisition_day CHECK (acquisition_day IS NULL OR (acquisition_day >= 1 AND acquisition_day <= 31)),
    CONSTRAINT chk_leave_acquisition_policies_monthly_rate CHECK (monthly_rate IS NULL OR monthly_rate >= 0),
    CONSTRAINT chk_leave_acquisition_policies_annual_quota CHECK (annual_quota IS NULL OR annual_quota >= 0),
    CONSTRAINT chk_leave_acquisition_policies_day_cap CHECK (day_cap IS NULL OR day_cap >= 0)
);

CREATE INDEX idx_leave_acquisition_policies_leave_type_id ON leave_acquisition_policies(leave_type_id);
CREATE INDEX idx_leave_acquisition_policies_active_dates ON leave_acquisition_policies(is_active, start_date, end_date);

CREATE TABLE leave_balance_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    leave_type_id UUID NOT NULL,
    transaction_type VARCHAR(80) NOT NULL,
    amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id UUID,
    comment TEXT,
    created_by_user_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_leave_balance_transactions_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_balance_transactions_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES leave_types(id),
    CONSTRAINT fk_leave_balance_transactions_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_leave_balance_transactions_employee_occurred_at
    ON leave_balance_transactions(employee_id, occurred_at DESC);
CREATE INDEX idx_leave_balance_transactions_leave_type_occurred_at
    ON leave_balance_transactions(leave_type_id, occurred_at DESC);
CREATE INDEX idx_leave_balance_transactions_source
    ON leave_balance_transactions(source_type, source_id);

INSERT INTO permissions (id, name, resource, action, scope, description, is_active)
VALUES
    ('55555555-5555-5555-5555-555555555147', 'HR_CALENDAR_READ', 'HR_CALENDAR', 'READ', 'GLOBAL', 'Read HR calendars and holidays', TRUE),
    ('55555555-5555-5555-5555-555555555148', 'HR_CALENDAR_MANAGE', 'HR_CALENDAR', 'MANAGE', 'GLOBAL', 'Manage HR calendars and holidays', TRUE),
    ('55555555-5555-5555-5555-555555555149', 'SETTINGS_MANAGE', 'SETTINGS', 'MANAGE', 'GLOBAL', 'Manage enterprise quick settings', TRUE),
    ('55555555-5555-5555-5555-555555555150', 'ACQUISITION_POLICY_READ', 'ACQUISITION_POLICY', 'READ', 'GLOBAL', 'Read leave acquisition policies', TRUE),
    ('55555555-5555-5555-5555-555555555151', 'ACQUISITION_POLICY_MANAGE', 'ACQUISITION_POLICY', 'MANAGE', 'GLOBAL', 'Manage leave acquisition policies', TRUE),
    ('55555555-5555-5555-5555-555555555152', 'LEAVE_BALANCE_READ_OWN', 'LEAVE_BALANCE', 'READ_OWN', 'OWN', 'Read own leave balances', TRUE),
    ('55555555-5555-5555-5555-555555555153', 'LEAVE_BALANCE_READ_SCOPED', 'LEAVE_BALANCE', 'READ_SCOPED', 'SCOPED', 'Read scoped leave balances', TRUE),
    ('55555555-5555-5555-5555-555555555154', 'LEAVE_BALANCE_MANAGE', 'LEAVE_BALANCE', 'MANAGE', 'GLOBAL', 'Adjust leave balances manually', TRUE)
ON CONFLICT (name) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_TYPE_READ'
)
WHERE profile.code = 'SELF_SERVICE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_BALANCE_READ_SCOPED',
    'LEAVE_TYPE_READ',
    'ACQUISITION_POLICY_READ',
    'HR_CALENDAR_READ'
)
WHERE profile.code = 'MANAGER_INBOX'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'HR_CALENDAR_READ',
    'HR_CALENDAR_MANAGE',
    'SETTINGS_MANAGE',
    'ACQUISITION_POLICY_READ',
    'ACQUISITION_POLICY_MANAGE',
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_BALANCE_READ_SCOPED',
    'LEAVE_BALANCE_MANAGE'
)
WHERE profile.code = 'HR_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO profile_permissions (profile_id, permission_id, granted_at, granted_by_id)
SELECT profile.id, permission.id, NOW(), NULL
FROM access_profiles profile
JOIN permissions permission ON permission.name IN (
    'HR_CALENDAR_READ',
    'HR_CALENDAR_MANAGE',
    'SETTINGS_MANAGE',
    'ACQUISITION_POLICY_READ',
    'ACQUISITION_POLICY_MANAGE',
    'LEAVE_BALANCE_READ_OWN',
    'LEAVE_BALANCE_READ_SCOPED',
    'LEAVE_BALANCE_MANAGE'
)
WHERE profile.code = 'ADMIN_CONSOLE'
ON CONFLICT (profile_id, permission_id) DO NOTHING;

INSERT INTO hr_calendars (
    id, code, name, country, timezone, hours_per_day, source, is_active, created_at, updated_at
)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'DEFAULT_TN',
    'Default Tunisia Calendar',
    'TN',
    'Africa/Tunis',
    8,
    'BOOTSTRAP',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    country = EXCLUDED.country,
    timezone = EXCLUDED.timezone,
    hours_per_day = EXCLUDED.hours_per_day,
    source = EXCLUDED.source,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

INSERT INTO hr_holidays (id, calendar_id, date, name, is_recurring, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    ph.date,
    ph.name,
    ph.is_recurring,
    NOW(),
    NOW()
FROM public_holidays ph
WHERE NOT EXISTS (
    SELECT 1
    FROM hr_holidays existing
    WHERE existing.calendar_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'
      AND existing.date = ph.date
      AND existing.name = ph.name
);

INSERT INTO enterprise_settings (
    id,
    singleton_key,
    monthly_acquisition_rate,
    max_authorizations_per_month,
    max_authorization_hours,
    work_week_pattern,
    default_validation_workflow_id,
    default_workflow_sla_hours,
    default_validation_sla_hours,
    active_calendar_id,
    working_hours_per_day,
    updated_at
)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
    TRUE,
    2,
    3,
    24,
    'MONDAY_TO_FRIDAY',
    NULL,
    72,
    72,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    8,
    NOW()
)
ON CONFLICT (singleton_key) DO UPDATE
SET monthly_acquisition_rate = COALESCE(enterprise_settings.monthly_acquisition_rate, EXCLUDED.monthly_acquisition_rate),
    max_authorizations_per_month = COALESCE(enterprise_settings.max_authorizations_per_month, EXCLUDED.max_authorizations_per_month),
    max_authorization_hours = COALESCE(enterprise_settings.max_authorization_hours, EXCLUDED.max_authorization_hours),
    work_week_pattern = COALESCE(enterprise_settings.work_week_pattern, EXCLUDED.work_week_pattern),
    default_workflow_sla_hours = COALESCE(enterprise_settings.default_workflow_sla_hours, EXCLUDED.default_workflow_sla_hours),
    default_validation_sla_hours = COALESCE(enterprise_settings.default_validation_sla_hours, EXCLUDED.default_validation_sla_hours),
    active_calendar_id = COALESCE(enterprise_settings.active_calendar_id, EXCLUDED.active_calendar_id),
    working_hours_per_day = COALESCE(enterprise_settings.working_hours_per_day, EXCLUDED.working_hours_per_day),
    updated_at = NOW();
