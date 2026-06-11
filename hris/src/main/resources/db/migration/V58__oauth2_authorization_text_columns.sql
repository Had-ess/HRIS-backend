-- Fix for the SAS JDBC schema adaptation in V57: JdbcOAuth2AuthorizationService
-- only writes binary when the JDBC column type is BLOB; PostgreSQL BYTEA reports
-- as BINARY, so the default parameter mapper binds the serialized JSON as a
-- VARCHAR and inserts fail with "column is of type bytea but expression is of
-- type character varying". The standard PostgreSQL adaptation is TEXT.
-- The table is empty pre-production, but USING keeps the conversion correct anyway.

ALTER TABLE oauth2_authorization
    ALTER COLUMN attributes                  TYPE TEXT USING convert_from(attributes, 'UTF8'),
    ALTER COLUMN authorization_code_value    TYPE TEXT USING convert_from(authorization_code_value, 'UTF8'),
    ALTER COLUMN authorization_code_metadata TYPE TEXT USING convert_from(authorization_code_metadata, 'UTF8'),
    ALTER COLUMN access_token_value          TYPE TEXT USING convert_from(access_token_value, 'UTF8'),
    ALTER COLUMN access_token_metadata       TYPE TEXT USING convert_from(access_token_metadata, 'UTF8'),
    ALTER COLUMN oidc_id_token_value         TYPE TEXT USING convert_from(oidc_id_token_value, 'UTF8'),
    ALTER COLUMN oidc_id_token_metadata      TYPE TEXT USING convert_from(oidc_id_token_metadata, 'UTF8'),
    ALTER COLUMN refresh_token_value         TYPE TEXT USING convert_from(refresh_token_value, 'UTF8'),
    ALTER COLUMN refresh_token_metadata      TYPE TEXT USING convert_from(refresh_token_metadata, 'UTF8'),
    ALTER COLUMN user_code_value             TYPE TEXT USING convert_from(user_code_value, 'UTF8'),
    ALTER COLUMN user_code_metadata          TYPE TEXT USING convert_from(user_code_metadata, 'UTF8'),
    ALTER COLUMN device_code_value           TYPE TEXT USING convert_from(device_code_value, 'UTF8'),
    ALTER COLUMN device_code_metadata        TYPE TEXT USING convert_from(device_code_metadata, 'UTF8');
