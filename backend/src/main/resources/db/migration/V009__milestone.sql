-- V009__milestone.sql
-- Milestone table for project-scoped milestone management.
-- Source: MF-BE-B4-001 Milestone Foundation CRUD.

-- ── milestone ────────────────────────────────────────────────────────────
-- Project-scoped milestone entity. Each milestone belongs to exactly one project
-- within a workspace. Per ADR-BE-006: workspace_id and project_id stored as UUID,
-- no JPA @ManyToOne. Per ADR-BE-005: full auditing fields.
--
-- Column types follow the database design doc (03_数据库表结构.md):
--   title varchar(180), description text.
-- B4-001 minimal status: OPEN (initial), COMPLETED (deferred to B4-002).
-- Full MilestoneStatus enum (DRAFT, READY, IN_PROGRESS, PENDING_ACCEPTANCE,
-- REVISION_REQUESTED, ACCEPTED, REJECTED, CANCELLED) will be introduced
-- in a later migration when the full status machine is implemented.
--
-- V008 project table does NOT have UNIQUE(workspace_id, id), so we use
-- simple FK to project(id) + application-layer workspace_id validation.
CREATE TABLE milestone (
    id              uuid            NOT NULL,
    workspace_id    uuid            NOT NULL,
    project_id      uuid            NOT NULL,
    title           varchar(180)    NOT NULL,
    description     text            NULL,
    due_date        date            NULL,
    status          varchar(32)     NOT NULL DEFAULT 'OPEN',
    completed_at    timestamptz     NULL,
    completed_by    uuid            NULL,
    settings        jsonb           NOT NULL DEFAULT '{}',
    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      uuid            NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      uuid            NULL,

    CONSTRAINT pk_milestone PRIMARY KEY (id),
    CONSTRAINT fk_milestone_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT,
    CONSTRAINT fk_milestone_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT,
    CONSTRAINT ck_milestone_status
        CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_milestone_settings
        CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_milestone_version
        CHECK (version >= 0),
    CONSTRAINT fk_milestone_created_by
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_milestone_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_milestone_completed_by
        FOREIGN KEY (completed_by) REFERENCES app_user(id) ON DELETE RESTRICT
);

-- Index for project-scoped milestone listing (filtered by status, sorted by due date)
CREATE INDEX idx_milestone_project_status_due
    ON milestone (project_id, status, due_date ASC NULLS LAST, created_at ASC);

-- Index for workspace-scoped milestone lookup
CREATE INDEX idx_milestone_workspace_project
    ON milestone (workspace_id, project_id);

-- Index for workspace-scoped listing sorted by creation time
CREATE INDEX idx_milestone_workspace_created
    ON milestone (workspace_id, created_at DESC);
