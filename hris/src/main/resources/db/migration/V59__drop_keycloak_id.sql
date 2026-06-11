-- =============================================================================
-- V59: Keycloak decommission (owned-auth migration, final phase)
-- =============================================================================
-- The embedded authorization server issues tokens whose sub IS users.id, so
-- the external identity column has no remaining readers (JIT provisioning was
-- removed with it). The legacy Keycloak schema in this database, if present,
-- can be dropped manually: DROP SCHEMA IF EXISTS keycloak CASCADE;

ALTER TABLE users DROP COLUMN IF EXISTS keycloak_id;
