-- =============================================================================
-- V61: Multi-tenancy phase 1 — tenants table + default tenant
-- =============================================================================
-- See docs/TENANCY_DESIGN.md. V61–V63 are deployable before any application
-- change: all existing data is backfilled into the default tenant and the app
-- keeps running single-tenant until the V64 cutover (RLS + app machinery).

CREATE TABLE tenants (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       VARCHAR(63)  NOT NULL UNIQUE,  -- subdomain-safe: acme -> acme.hris.app
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_tenants_slug CHECK (slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$')
);

-- Fixed UUID so application code and later migrations can reference it.
INSERT INTO tenants (id, slug, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Tenant');
