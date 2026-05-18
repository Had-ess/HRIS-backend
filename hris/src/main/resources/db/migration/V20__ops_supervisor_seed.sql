-- Seeds the operations supervisor Keycloak demo user into HRIS.
-- Uses placeholder keycloak_id; JIT provisioning adopts this record on first login via email match.

BEGIN;

INSERT INTO users (id, keycloak_id, email, first_name, last_name, locale_preference, is_seed, is_active)
VALUES (
    '33333333-3333-3333-3333-333333333308',
    'KC_DEMO_OPS_SUPERVISOR',
    'supervisor.operations@demo.hris.local',
    'Amine',
    'Zouari',
    'fr',
    TRUE,
    TRUE
)
ON CONFLICT (email) DO UPDATE
SET keycloak_id        = EXCLUDED.keycloak_id,
    first_name         = EXCLUDED.first_name,
    last_name          = EXCLUDED.last_name,
    locale_preference  = EXCLUDED.locale_preference,
    is_seed            = EXCLUDED.is_seed,
    is_active          = EXCLUDED.is_active;

INSERT INTO user_profile_assignments (user_id, profile_id, assigned_at, assigned_by_id, is_active)
SELECT
    (SELECT id FROM users WHERE email = 'supervisor.operations@demo.hris.local'),
    id,
    TIMESTAMPTZ '2026-01-01 09:20:00+00',
    (SELECT id FROM users WHERE email = 'admin@demo.hris.local'),
    TRUE
FROM access_profiles
WHERE code = 'MANAGER_INBOX'
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_assignments existing
      WHERE existing.user_id = (SELECT id FROM users WHERE email = 'supervisor.operations@demo.hris.local')
        AND existing.profile_id = access_profiles.id
  );

COMMIT;
