-- V008__project.sql
-- Project table for workspace-scoped project management.
-- Source: MF-BE-B3-001 Project Foundation CRUD.

-- ── project ─────────────────────────────────────────────────────────────
-- Workspace-scoped project entity. Each project belongs to exactly one workspace.
-- Per ADR-BE-006: workspace_id stored as UUID, no JPA @ManyToOne.
-- Per ADR-BE-005: full auditing fields (created_at, created_by, updated_at, updated_by, version).
CREATE TABLE project (
    id              uuid            NOT NULL,
    workspace_id    uuid            NOT NULL,
    name            varchar(120)    NOT NULL,
    description     varchar(2000)   NULL,
    status          varchar(32)     NOT NULL DEFAULT 'ACTIVE',
    start_date      date            NULL,
    target_date     date            NULL,
    settings        jsonb           NOT NULL DEFAULT '{}',
    archived_at     timestamptz     NULL,
    archived_by     uuid            NULL,
    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      uuid            NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      uuid            NULL,

    CONSTRAINT pk_project PRIMARY KEY (id),
    CONSTRAINT fk_project_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_project_settings
        CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_project_version
        CHECK (version >= 0),
    CONSTRAINT ck_project_date_range
        CHECK (start_date IS NULL OR target_date IS NULL OR start_date <= target_date),
    CONSTRAINT fk_project_created_by
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_archived_by
        FOREIGN KEY (archived_by) REFERENCES app_user(id) ON DELETE RESTRICT
);

-- Index for workspace-scoped project listing (filtered by status, sorted by update time)
CREATE INDEX idx_project_workspace_status_updated
    ON project (workspace_id, status, updated_at DESC);

-- Index for workspace-scoped project listing sorted by creation time
CREATE INDEX idx_project_workspace_created
    ON project (workspace_id, created_at DESC);
