-- V010__task.sql
-- Task table for milestone-scoped task management.
-- Source: MF-BE-B5-001 Task Foundation CRUD.

-- ── task ─────────────────────────────────────────────────────────────────
-- Milestone-scoped task entity. Each task belongs to exactly one milestone
-- within a project within a workspace. Per ADR-BE-006: workspace_id, project_id,
-- and milestone_id stored as UUID, no JPA @ManyToOne. Per ADR-BE-005: full auditing fields.
--
-- Column types follow the database design pattern from V009:
--   title varchar(160), description varchar(4000).
-- B5-001 minimal status: OPEN (initial), COMPLETED (reserved for B5-002).
-- B5-001 minimal priority: LOW, MEDIUM, HIGH (required on creation).
-- completed_at / completed_by reserved for B5-002 (task completion workflow).
CREATE TABLE task (
    id              uuid            NOT NULL,
    workspace_id    uuid            NOT NULL,
    project_id      uuid            NOT NULL,
    milestone_id    uuid            NOT NULL,

    title           varchar(160)    NOT NULL,
    description     varchar(4000)   NULL,
    status          varchar(32)     NOT NULL DEFAULT 'OPEN',
    priority        varchar(32)     NOT NULL DEFAULT 'MEDIUM',

    due_date        date            NULL,
    completed_at    timestamptz     NULL,
    completed_by    uuid            NULL,

    settings        jsonb           NOT NULL DEFAULT '{}',

    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      uuid            NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      uuid            NULL,

    CONSTRAINT pk_task PRIMARY KEY (id),
    CONSTRAINT fk_task_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_milestone
        FOREIGN KEY (milestone_id) REFERENCES milestone(id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_status
        CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_task_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_task_settings
        CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_task_version
        CHECK (version >= 0),
    CONSTRAINT fk_task_created_by
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_completed_by
        FOREIGN KEY (completed_by) REFERENCES app_user(id) ON DELETE RESTRICT
);

-- Index for milestone-scoped task listing (filtered by status, sorted by due date)
CREATE INDEX idx_task_milestone_status_due
    ON task (milestone_id, status, due_date ASC NULLS LAST, created_at ASC);

-- Index for workspace-scoped task lookup
CREATE INDEX idx_task_workspace_project_milestone
    ON task (workspace_id, project_id, milestone_id);

-- Index for workspace-scoped listing sorted by creation time
CREATE INDEX idx_task_workspace_created
    ON task (workspace_id, created_at DESC);
