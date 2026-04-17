-- Create keycloak schema for Keycloak tables
CREATE SCHEMA IF NOT EXISTS keycloak;

-- Create public schema for application tables (default, but explicit)
CREATE SCHEMA IF NOT EXISTS public;

-- Grant privileges
GRANT ALL PRIVILEGES ON SCHEMA keycloak TO hris_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO hris_user;
