-- V002__identity.sql
-- Identity tables: app_user, auth_session, verification_token.
-- Source: B1_AUTHENTICATION_BASELINE §16.1–16.3 (Frozen).

-- ── app_user ──────────────────────────────────────────────────────────────
-- Global login identity. Workspace roles are expressed via workspace_membership.
CREATE TABLE app_user (
    id                uuid            NOT NULL,
    email             varchar(320)    NOT NULL,
    email_normalized  varchar(320)    NOT NULL,
    display_name      varchar(100)    NOT NULL,
    password_hash     varchar(255)    NOT NULL,
    status            varchar(32)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    locale            varchar(16)     NOT NULL DEFAULT 'zh-TW',
    email_verified_at timestamptz     NULL,
    last_login_at     timestamptz     NULL,
    version           bigint          NOT NULL DEFAULT 0,
    created_at        timestamptz     NOT NULL DEFAULT now(),
    created_by        uuid            NULL,
    updated_at        timestamptz     NOT NULL DEFAULT now(),
    updated_by        uuid            NULL,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_app_user_status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_app_user_version CHECK (version >= 0)
);

-- ── auth_session ──────────────────────────────────────────────────────────
-- Opaque token session with family-based refresh rotation.
-- Only SHA-256 hashes are stored — never raw tokens.
CREATE TABLE auth_session (
    id                   uuid          NOT NULL,
    user_id              uuid          NOT NULL,
    access_token_hash    varchar(64)   NOT NULL,
    refresh_token_hash   varchar(64)   NOT NULL,
    session_family_id    uuid          NOT NULL,
    refresh_generation   bigint        NOT NULL DEFAULT 0,
    status               varchar(24)   NOT NULL DEFAULT 'ACTIVE',
    user_agent           varchar(512)  NULL,
    ip_address           varchar(45)   NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    expires_at           timestamptz   NOT NULL,
    last_used_at         timestamptz   NULL,
    revoked_at           timestamptz   NULL,
    revoke_reason        varchar(48)   NULL,

    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT uk_auth_session_access_hash UNIQUE (access_token_hash),
    CONSTRAINT uk_auth_session_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT uk_auth_session_family_gen UNIQUE (session_family_id, refresh_generation),
    CONSTRAINT fk_auth_session_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_auth_session_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_auth_session_generation
        CHECK (refresh_generation >= 0),
    CONSTRAINT ck_auth_session_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_session_revoked
        CHECK ((status = 'REVOKED' AND revoked_at IS NOT NULL) OR (status <> 'REVOKED'))
);

-- ── verification_token ────────────────────────────────────────────────────
-- Single-use security token hashes for email verification and password reset.
CREATE TABLE verification_token (
    id           uuid          NOT NULL,
    user_id      uuid          NOT NULL,
    purpose      varchar(48)   NOT NULL,
    token_hash   varchar(64)   NOT NULL,
    expires_at   timestamptz   NOT NULL,
    used_at      timestamptz   NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_verification_token PRIMARY KEY (id),
    CONSTRAINT uk_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_verification_token_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_verification_token_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_verification_token_expiry
        CHECK (expires_at > created_at)
);
