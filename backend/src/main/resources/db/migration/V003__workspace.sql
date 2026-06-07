-- V003__workspace.sql
-- Workspace and membership tables (structural only in B1).
-- Source: B1_AUTHENTICATION_BASELINE §16.4–16.5 (Frozen).

-- ── workspace ─────────────────────────────────────────────────────────────
-- Tenant boundary, currency boundary, timezone boundary.
-- Workspace root table — does NOT contain workspace_id.
CREATE TABLE workspace (
    id                uuid            NOT NULL,
    name              varchar(120)    NOT NULL,
    slug              varchar(80)     NOT NULL,
    default_currency  char(3)         NOT NULL DEFAULT 'TWD',
    timezone          varchar(64)     NOT NULL DEFAULT 'Asia/Taipei',
    status            varchar(24)     NOT NULL DEFAULT 'ACTIVE',
    settings          jsonb           NOT NULL DEFAULT '{}',
    archived_at       timestamptz     NULL,
    archived_by       uuid            NULL,
    version           bigint          NOT NULL DEFAULT 0,
    created_at        timestamptz     NOT NULL DEFAULT now(),
    created_by        uuid            NULL,
    updated_at        timestamptz     NOT NULL DEFAULT now(),
    updated_by        uuid            NULL,

    CONSTRAINT pk_workspace PRIMARY KEY (id),
    CONSTRAINT uk_workspace_slug UNIQUE (slug),
    CONSTRAINT ck_workspace_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_workspace_currency
        CHECK (default_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_workspace_settings
        CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_workspace_version CHECK (version >= 0),
    CONSTRAINT ck_workspace_archived
        CHECK ((status = 'ARCHIVED' AND archived_at IS NOT NULL) OR (status <> 'ARCHIVED'))
);

-- ── workspace_membership ──────────────────────────────────────────────────
-- User ↔ Workspace relationship with role and status.
-- V0.1 role CHECK only includes OWNER (additional roles in V0.2 via migration).
CREATE TABLE workspace_membership (
    id              uuid            NOT NULL,
    workspace_id    uuid            NOT NULL,
    user_id         uuid            NOT NULL,
    role            varchar(24)     NOT NULL DEFAULT 'OWNER',
    status          varchar(24)     NOT NULL DEFAULT 'ACTIVE',
    joined_at       timestamptz     NOT NULL DEFAULT now(),
    ended_at        timestamptz     NULL,
    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_workspace_membership PRIMARY KEY (id),
    CONSTRAINT uk_workspace_membership UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_membership_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER')),
    CONSTRAINT ck_membership_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    CONSTRAINT ck_membership_version CHECK (version >= 0),
    CONSTRAINT ck_membership_active_joined
        CHECK ((status = 'ACTIVE' AND joined_at IS NOT NULL) OR (status <> 'ACTIVE')),
    CONSTRAINT ck_membership_removed_ended
        CHECK ((status = 'REMOVED' AND ended_at IS NOT NULL) OR (status <> 'REMOVED')),
    CONSTRAINT ck_membership_ended_after_joined
        CHECK (ended_at IS NULL OR ended_at >= joined_at)
);

-- One ACTIVE OWNER per workspace (partial unique index)
CREATE UNIQUE INDEX uk_workspace_membership_active_owner
    ON workspace_membership (workspace_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';

-- One ACTIVE membership per user in V0.1 (partial unique index)
-- Future multi-workspace support requires a new migration to drop this index.
CREATE UNIQUE INDEX uk_workspace_membership_active_user
    ON workspace_membership (user_id)
    WHERE status = 'ACTIVE';
