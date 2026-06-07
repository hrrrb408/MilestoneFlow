-- V005__authentication_indexes.sql
-- Query indexes for authentication and audit tables.
-- Unique constraint indexes are created with the table (V002–V004).

-- ── auth_session ─────────────────────────────────────────────────────────
CREATE INDEX idx_auth_session_user_status
    ON auth_session (user_id, status);

CREATE INDEX idx_auth_session_family_status
    ON auth_session (session_family_id, status);

-- ── verification_token ────────────────────────────────────────────────────
CREATE INDEX idx_verification_token_user_purpose
    ON verification_token (user_id, purpose);

CREATE INDEX idx_verification_token_lookup
    ON verification_token (token_hash, purpose)
    WHERE used_at IS NULL;

-- ── workspace_membership ─────────────────────────────────────────────────
CREATE INDEX idx_workspace_membership_workspace_status
    ON workspace_membership (workspace_id, status);

CREATE INDEX idx_workspace_membership_user_status
    ON workspace_membership (user_id, status);

-- ── audit_event ──────────────────────────────────────────────────────────
CREATE INDEX idx_audit_event_actor_time
    ON audit_event (actor_id, created_at DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX idx_audit_event_target_time
    ON audit_event (target_type, target_id, created_at DESC);

CREATE INDEX idx_audit_event_workspace_time
    ON audit_event (workspace_id, created_at DESC)
    WHERE workspace_id IS NOT NULL;

CREATE INDEX idx_audit_event_request
    ON audit_event (request_id)
    WHERE request_id IS NOT NULL;
