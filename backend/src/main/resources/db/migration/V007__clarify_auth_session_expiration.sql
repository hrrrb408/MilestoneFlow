-- V007__clarify_auth_session_expiration.sql
-- Split auth_session.expires_at into two explicit fields:
--   refresh_expires_at — when the refresh token expires (was expires_at per B1 §6.1)
--   access_expires_at  — when the access token expires
--
-- Rationale:
--   B1 §6.1 defines expires_at as "Refresh token expiry (30 days from creation)".
--   B1 §2.1 defines access token validity as 15 minutes, checked server-side.
--   A single expires_at cannot express both timelines. This migration gives each
--   token type its own expiration column so the server can independently validate
--   access vs refresh token lifetimes.
--
-- Backfill:
--   access_expires_at = LEAST(created_at + 15 minutes, refresh_expires_at)
--   This ensures access expires no later than refresh, using the B1 15-minute window.
--   For CI-only data this is safe. For production data it preserves the constraint
--   refresh_expires_at >= access_expires_at.
--
-- The database does NOT hard-code 15 minutes or 30 days as defaults.
-- Expiration times are calculated by the application service using an injected Clock
-- and configuration, then written to these columns.

-- Step 1: Rename expires_at → refresh_expires_at (preserving B1 semantics)
ALTER TABLE auth_session RENAME COLUMN expires_at TO refresh_expires_at;

-- Step 2: Add access_expires_at (initially nullable for safe backfill)
ALTER TABLE auth_session ADD COLUMN access_expires_at timestamptz;

-- Step 3: Backfill — access expires 15 minutes after creation, but never after refresh
UPDATE auth_session
SET access_expires_at = LEAST(created_at + INTERVAL '15 minutes', refresh_expires_at);

-- Step 4: Make access_expires_at NOT NULL (all rows now populated)
ALTER TABLE auth_session ALTER COLUMN access_expires_at SET NOT NULL;

-- Step 5: Replace old single-expiry constraint with separate constraints
ALTER TABLE auth_session DROP CONSTRAINT ck_auth_session_expiry;

ALTER TABLE auth_session ADD CONSTRAINT ck_auth_session_access_expiry
    CHECK (access_expires_at > created_at);

ALTER TABLE auth_session ADD CONSTRAINT ck_auth_session_refresh_expiry
    CHECK (refresh_expires_at > created_at);

ALTER TABLE auth_session ADD CONSTRAINT ck_auth_session_refresh_after_access
    CHECK (refresh_expires_at >= access_expires_at);
