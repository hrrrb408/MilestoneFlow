-- V004__audit.sql
-- Immutable audit event table with append-only protection.
-- Source: B1_AUTHENTICATION_BASELINE §16.6 (Frozen).

-- ── audit_event ───────────────────────────────────────────────────────────
-- workspace_id is nullable: identity events (registration, login) occur
-- before any workspace exists. Workspace-scoped events must carry workspace_id.
CREATE TABLE audit_event (
    id              uuid            NOT NULL,
    actor_id        uuid            NULL,
    actor_type      varchar(24)     NOT NULL,
    action          varchar(64)     NOT NULL,
    target_type     varchar(48)     NULL,
    target_id       uuid            NULL,
    workspace_id    uuid            NULL,
    request_id      varchar(36)     NULL,
    source          varchar(24)     NOT NULL DEFAULT 'API',
    summary         varchar(500)    NOT NULL,
    metadata        jsonb           NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT ck_audit_event_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM', 'JOB')),
    CONSTRAINT ck_audit_event_source
        CHECK (source IN ('API', 'INTERNAL', 'JOB', 'CRON')),
    CONSTRAINT ck_audit_event_actor_user
        CHECK ((actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type <> 'USER')),
    CONSTRAINT fk_audit_event_actor
        FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_event_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT
);

-- ── Append-only protection ───────────────────────────────────────────────
-- Prevents UPDATE and DELETE on audit_event.
-- INSERT remains allowed. The trigger fires before mutation and raises an error.
CREATE OR REPLACE FUNCTION fn_reject_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'AUDIT_EVENT_IMMUTABLE';
END;
$$;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION fn_reject_audit_mutation();

CREATE TRIGGER trg_audit_event_no_delete
    BEFORE DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION fn_reject_audit_mutation();
