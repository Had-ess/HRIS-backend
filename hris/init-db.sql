-- Create public schema for application tables (default, but explicit)
CREATE SCHEMA IF NOT EXISTS public;

-- Grant privileges
GRANT ALL PRIVILEGES ON SCHEMA public TO hris_user;
