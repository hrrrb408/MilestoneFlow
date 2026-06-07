# MF-BE-005: Authentication Database Schema

## Task Scope

MF-BE-005 creates the B1 authentication database schema via Flyway migrations V002–V005. This is pure database structure — no Java Entity, Repository, Service, or Controller.

## Migration Files

| Migration | Content | Tables |
|-----------|---------|--------|
| V002 | Identity | app_user, auth_session, verification_token |
| V003 | Workspace | workspace, workspace_membership |
| V004 | Audit | audit_event + append-only trigger |
| V005 | Indexes | Query indexes for all tables |

V001 (bootstrap) was not modified.

## Table Summary

### app_user

Global login identity. Workspace roles are expressed via `workspace_membership`.

- **PK**: `id uuid` (application-generated UUID v7)
- **Unique**: `email_normalized`
- **CHECK**: status ∈ {PENDING_VERIFICATION, ACTIVE, DISABLED}; version ≥ 0
- **FK**: None (root table)
- **Delete**: Protected by RESTRICT from auth_session, verification_token, workspace_membership

### auth_session

Opaque token session with family-based refresh rotation.

- **PK**: `id uuid`
- **Token Hash**: SHA-256 hex stored in `access_token_hash varchar(64)` and `refresh_token_hash varchar(64)` — never raw tokens
- **Family**: `session_family_id uuid NOT NULL` groups sessions from the same login chain
- **Generation**: `refresh_generation bigint ≥ 0`, unique within a family
- **Status**: ACTIVE / REVOKED / EXPIRED
- **Expiry**: `expires_at > created_at`
- **Revoked consistency**: REVOKED requires `revoked_at IS NOT NULL`
- **Unique**: access_token_hash, refresh_token_hash, (session_family_id, refresh_generation)
- **FK**: user_id → app_user(id) ON DELETE RESTRICT
- **Delete**: Protected by RESTRICT

### verification_token

Single-use security token hashes for email verification and password reset.

- **Purpose**: EMAIL_VERIFICATION / PASSWORD_RESET (single table, differentiated by purpose)
- **Hash**: `token_hash varchar(64)` — SHA-256 hex, never raw tokens
- **Expiry**: `expires_at > created_at`
- **Single use**: tracked via `used_at` (nullable)
- **Unique**: token_hash
- **FK**: user_id → app_user(id) ON DELETE RESTRICT

### workspace

Tenant boundary, currency boundary, timezone boundary. Root table — no `workspace_id`.

- **Slug**: globally unique URL-safe identifier
- **Currency**: `char(3)` uppercase ISO 4217, validated by regex `^[A-Z]{3}$`
- **Settings**: `jsonb NOT NULL DEFAULT '{}'`, validated as JSON object via `jsonb_typeof(settings) = 'object'`
- **Status**: ACTIVE / SUSPENDED / ARCHIVED
- **Archive consistency**: ARCHIVED requires `archived_at IS NOT NULL`
- **Audit fields**: created_by, updated_by, archived_by → app_user(id)

### workspace_membership

User ↔ Workspace relationship.

- **Role**: V0.1 only OWNER (additional roles in V0.2 via Flyway migration)
- **Status**: PENDING / ACTIVE / REMOVED
- **Active consistency**: ACTIVE requires `joined_at IS NOT NULL`
- **Removed consistency**: REMOVED requires `ended_at IS NOT NULL`
- **Date order**: `ended_at IS NULL OR ended_at >= joined_at`
- **Unique**: (workspace_id, user_id)
- **Partial unique**: one ACTIVE OWNER per workspace; one ACTIVE membership per user (V0.1)

### audit_event

Immutable business and security audit log.

- **workspace_id nullable**: Identity events (registration, login) occur before workspace creation
- **Actor**: `actor_type` ∈ {USER, SYSTEM, JOB}; USER requires `actor_id IS NOT NULL`
- **Source**: ∈ {API, INTERNAL, JOB, CRON}
- **Metadata**: `jsonb NULL` — never contains passwords, tokens, cookies, or request bodies
- **No update_at / deleted_at**: This is an append-only table
- **Append-only protection**: Trigger `fn_reject_audit_mutation()` rejects UPDATE and DELETE

## Token Hash Principle

All tokens (access, refresh, verification) are stored as SHA-256 hex hashes (`varchar(64)`). The database never stores:
- Raw token values
- Passwords (only `password_hash`)
- Cookie values
- Authorization headers

## Index Summary

| Index | Table | Columns | Unique | Partial Condition |
|-------|-------|---------|--------|-------------------|
| uk_app_user_email_normalized | app_user | email_normalized | Yes | — |
| uk_auth_session_access_hash | auth_session | access_token_hash | Yes | — |
| uk_auth_session_refresh_hash | auth_session | refresh_token_hash | Yes | — |
| uk_auth_session_family_gen | auth_session | session_family_id, refresh_generation | Yes | — |
| uk_workspace_slug | workspace | slug | Yes | — |
| uk_workspace_membership | workspace_membership | workspace_id, user_id | Yes | — |
| uk_workspace_membership_active_owner | workspace_membership | workspace_id | Yes | role='OWNER' AND status='ACTIVE' |
| uk_workspace_membership_active_user | workspace_membership | user_id | Yes | status='ACTIVE' |
| uk_verification_token_hash | verification_token | token_hash | Yes | — |
| idx_auth_session_user_status | auth_session | user_id, status | No | — |
| idx_auth_session_family_status | auth_session | session_family_id, status | No | — |
| idx_verification_token_user_purpose | verification_token | user_id, purpose | No | — |
| idx_verification_token_lookup | verification_token | token_hash, purpose | No | used_at IS NULL |
| idx_workspace_membership_workspace_status | workspace_membership | workspace_id, status | No | — |
| idx_workspace_membership_user_status | workspace_membership | user_id, status | No | — |
| idx_audit_event_actor_time | audit_event | actor_id, created_at DESC | No | actor_id IS NOT NULL |
| idx_audit_event_target_time | audit_event | target_type, target_id, created_at DESC | No | — |
| idx_audit_event_workspace_time | audit_event | workspace_id, created_at DESC | No | workspace_id IS NOT NULL |
| idx_audit_event_request | audit_event | request_id | No | request_id IS NOT NULL |

No duplicate indexes found. No JSONB GIN indexes created (no JSONB query requirements yet).

## Workspace Isolation

V0.1 uses application-layer `workspaceId` filtering + composite FK + integration tests.

No PostgreSQL RLS (`CREATE POLICY` / `ENABLE ROW LEVEL SECURITY`) is used.

## Primary Key Generation

All PKs are `uuid` type with **no database default**. IDs are generated application-side via `IdGenerator` (UUID v7). No `gen_random_uuid()`, no `serial`, no `identity`.

## Test Coverage

Integration tests (Testcontainers PostgreSQL 17):

| Category | Tests | Verified SQLSTATEs |
|----------|-------|--------------------|
| Migration history & schema | ~20 | — |
| app_user constraints | 10 | 23502, 23505, 23514 |
| auth_session constraints | 13 | 23502, 23503, 23505, 23514 |
| verification_token constraints | 7 | 23502, 23503, 23505, 23514 |
| workspace constraints | 10 | 23505, 23514 |
| workspace_membership constraints | 12 | 23502, 23503, 23505, 23514 |
| audit_event constraints | 10 | 23502, 23503, 23514 |
| Index structure | 12 | — |
| Concurrency | 2 | 23505 |

## Future Java ORM Notes

- Entities should map `id` fields without `@GeneratedValue` (application-generated)
- `settings` and `metadata` use `@JdbcTypeCode(SqlTypes.JSON)` (ADR-BE-004)
- Auditing uses JPA `@CreatedDate`/`@LastModifiedDate` with `Clock` bean (ADR-BE-005)
- Tenant business tables need composite `UNIQUE(workspace_id, id)` for child FK (ADR-BE-006)
- `workspace_membership.role` CHECK only includes OWNER — V0.2 migration adds ADMIN, MEMBER, VIEWER
- `uk_workspace_membership_active_user` partial unique index is V0.1 only — multi-workspace support requires a migration to drop it

## Known Limitations

- No PostgreSQL RLS — application layer enforces tenant isolation
- V0.1 single-workspace-per-user constraint enforced by partial unique index
- Audit append-only trigger raises exception but does not log the attempt (application should catch and log)
- `app_user.created_by` is NULL for self-registration (no authenticated user exists yet)
- No email format validation at database level (application validation handles this)
