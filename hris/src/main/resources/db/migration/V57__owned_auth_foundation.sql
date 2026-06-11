-- =============================================================================
-- V57: Owned authentication foundation (Keycloak removal, phase 1)
-- =============================================================================
-- The backend becomes its own OAuth2/OIDC authorization server (Spring
-- Authorization Server). See docs/AUTH_MIGRATION_DESIGN.md.
--
--   user_credentials      local password storage (BCrypt) + lockout state
--   user_action_tokens    single-use activation / password-reset tokens (hashed)
--   signing_keys          persisted RSA keys backing the JWKS endpoint
--   oauth2_authorization  Spring Authorization Server JDBC store (refresh
--                         tokens survive restarts and are revocable)
--
-- users.keycloak_id becomes nullable: locally-provisioned accounts no longer
-- have a Keycloak identity. Column is dropped entirely at decommission.
-- =============================================================================

ALTER TABLE users ALTER COLUMN keycloak_id DROP NOT NULL;

CREATE TABLE user_credentials (
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    password_hash        VARCHAR(100) NOT NULL,
    password_updated_at  TIMESTAMPTZ  NOT NULL,
    failed_attempts      INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ
);

COMMENT ON TABLE user_credentials IS
    'Local login credentials; absence means the account has not been activated yet';

CREATE TABLE user_action_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,  -- SHA-256 hex; the raw token is never stored
    purpose     VARCHAR(20)  NOT NULL,  -- ACTIVATION | PASSWORD_RESET
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_user_action_tokens_hash ON user_action_tokens (token_hash);
CREATE INDEX idx_user_action_tokens_user ON user_action_tokens (user_id, purpose);

CREATE TABLE signing_keys (
    kid          VARCHAR(36)  PRIMARY KEY,
    public_key   TEXT         NOT NULL,  -- Base64 X.509
    private_key  TEXT         NOT NULL,  -- Base64 PKCS#8 (encrypt at rest before prod)
    status       VARCHAR(10)  NOT NULL,  -- ACTIVE | RETIRED
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Spring Authorization Server JDBC schema (official layout; blob columns
-- adapted to bytea for PostgreSQL).
CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100) PRIMARY KEY,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000),
    attributes                    BYTEA,
    state                         VARCHAR(500),
    authorization_code_value      BYTEA,
    authorization_code_issued_at  TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata   BYTEA,
    access_token_value            BYTEA,
    access_token_issued_at        TIMESTAMPTZ,
    access_token_expires_at       TIMESTAMPTZ,
    access_token_metadata         BYTEA,
    access_token_type             VARCHAR(100),
    access_token_scopes           VARCHAR(1000),
    oidc_id_token_value           BYTEA,
    oidc_id_token_issued_at       TIMESTAMPTZ,
    oidc_id_token_expires_at      TIMESTAMPTZ,
    oidc_id_token_metadata        BYTEA,
    refresh_token_value           BYTEA,
    refresh_token_issued_at       TIMESTAMPTZ,
    refresh_token_expires_at      TIMESTAMPTZ,
    refresh_token_metadata        BYTEA,
    user_code_value               BYTEA,
    user_code_issued_at           TIMESTAMPTZ,
    user_code_expires_at          TIMESTAMPTZ,
    user_code_metadata            BYTEA,
    device_code_value             BYTEA,
    device_code_issued_at         TIMESTAMPTZ,
    device_code_expires_at        TIMESTAMPTZ,
    device_code_metadata          BYTEA
);

-- Session revocation deletes by principal (email).
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization (principal_name);
