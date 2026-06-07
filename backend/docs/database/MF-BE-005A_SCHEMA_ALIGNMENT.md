# MF-BE-005A: Schema Alignment — Index, Expiration, and Audit Model Decisions

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-06-07 |
| Task | MF-BE-005A |
| Blocks Until | MF-BE-006 proceeds |
| Based On | B1_AUTHENTICATION_BASELINE v1.0 (Frozen), ADR Review Report (Accepted) |

---

## 1. Duplicate Index: `idx_auth_session_family`

### 1.1 Problem

`auth_session` had two indexes on the same columns `(session_family_id, refresh_generation)`:

| Index | Type | Source |
|-------|------|--------|
| `uk_auth_session_family_gen` | UNIQUE B-tree | Implicit from UNIQUE constraint (V002) |
| `idx_auth_session_family` | Non-unique B-tree | Explicit CREATE INDEX (V005) |

Both indexes cover the same table, same columns, same column order, same B-tree method, no additional predicate, no INCLUDE columns. The non-unique index is therefore a complete duplicate.

### 1.2 Decision

- **Remove**: `idx_auth_session_family` via forward migration V006.
- **Preserve**: `uk_auth_session_family_gen` (the authoritative unique constraint index).
- **Preserve**: `idx_auth_session_family_status` (different columns: `session_family_id, status`).

### 1.3 Migration

- **V006** `remove_redundant_auth_session_family_index.sql`: `DROP INDEX IF EXISTS idx_auth_session_family`
- V001–V005 not modified.
- No `CASCADE`, no concurrent drop needed (CI-only data, minimal size).

---

## 2. auth_session Expiration Semantics

### 2.1 Problem

B1 §6.1 defines a single `expires_at` with semantics "Refresh token expiry (30 days from creation)". Simultaneously, B1 §2.1 defines access token validity as 15 minutes, requiring server-side expiry checking.

A single `expires_at` field cannot express both timelines:
- **Access Token**: 15-minute validity, checked on every request.
- **Refresh Token**: 30-day validity, checked during refresh.

The server needs independent fields to determine "is the access token still valid?" vs "is the refresh token still valid?".

### 2.2 Decision

Replace the single `expires_at` with two explicit fields:

| Field | Type | Semantics |
|-------|------|-----------|
| `access_expires_at` | `timestamptz NOT NULL` | When the access token expires (typically 15 minutes from `created_at`) |
| `refresh_expires_at` | `timestamptz NOT NULL` | When the refresh token expires (typically 30 days from `created_at`) |

The old `expires_at` is renamed to `refresh_expires_at` (preserving its documented B1 semantics).

### 2.3 Old Field Semantics

| Old Field | B1 §6.1 Definition | New Mapping |
|-----------|-------------------|-------------|
| `expires_at` | "Refresh token expiry (30 days from creation)" | → `refresh_expires_at` |

The B1 baseline explicitly defines `expires_at` as the **refresh token** expiry. The rename is consistent with its original documented semantics.

### 2.4 Backfill Algorithm

For existing rows (CI-only data):

```sql
access_expires_at = LEAST(created_at + INTERVAL '15 minutes', refresh_expires_at)
```

**Why this is safe**:
- `created_at + 15 minutes` uses the documented access token window.
- `LEAST(..., refresh_expires_at)` ensures the constraint `refresh_expires_at >= access_expires_at` is always satisfied.
- For CI data where sessions are freshly created, `created_at + 15 minutes` will always be less than `refresh_expires_at` (30 days).
- No `now()` used — backfill is deterministic from existing data, not migration execution time.

### 2.5 New Constraints

| Constraint | Check | Purpose |
|-----------|-------|---------|
| `ck_auth_session_access_expiry` | `access_expires_at > created_at` | Access must expire after creation |
| `ck_auth_session_refresh_expiry` | `refresh_expires_at > created_at` | Refresh must expire after creation |
| `ck_auth_session_refresh_after_access` | `refresh_expires_at >= access_expires_at` | Refresh cannot expire before access |

Old constraint `ck_auth_session_expiry` (`expires_at > created_at`) is dropped.

### 2.6 No Database-Enforced TTL

The database does NOT set default expiration durations (no 15-minute or 30-day defaults). Application services compute exact expiration timestamps using an injected `Clock` and configuration, then write them to these columns. This preserves testability and configurability.

### 2.7 Migration

- **V007** `clarify_auth_session_expiration.sql`:
  1. Rename `expires_at` → `refresh_expires_at`
  2. Add `access_expires_at timestamptz` (nullable initially)
  3. Backfill `access_expires_at`
  4. Set `access_expires_at NOT NULL`
  5. Drop `ck_auth_session_expiry`
  6. Add three new CHECK constraints

### 2.8 MF-BE-006 ORM Mapping Requirements

- Map `access_expires_at` as `Instant` (per ADR-BE-005 timestamp type).
- Map `refresh_expires_at` as `Instant`.
- No `@GeneratedValue` on any ID field.
- Application service computes both expiration timestamps using `Clock` + config.

---

## 3. audit_event Schema Decision

### 3.1 Current Implementation (V004)

```sql
CREATE TABLE audit_event (
    id              uuid            NOT NULL,
    actor_id        uuid            NULL,
    actor_type      varchar(24)     NOT NULL,    -- USER / SYSTEM / JOB
    action          varchar(64)     NOT NULL,
    target_type     varchar(48)     NULL,
    target_id       uuid            NULL,
    workspace_id    uuid            NULL,
    request_id      varchar(36)     NULL,
    source          varchar(24)     NOT NULL DEFAULT 'API',  -- API / INTERNAL / JOB / CRON
    summary         varchar(500)    NOT NULL,
    metadata        jsonb           NULL,
    created_at      timestamptz     NOT NULL DEFAULT now()
);
```

### 3.2 B1 Authentication Baseline Position

B1 §16.6 (Frozen) explicitly defines this exact structure for audit_event. The ADR Review Report approved B1 without blocking conditions. Per the document priority hierarchy:

```
B1_AUTHENTICATION_BASELINE (Frozen, Accepted)
> ADR_REVIEW_REPORT (Accepted)
> Database Baseline Candidate (pre-approval draft)
```

**Conclusion**: This is **Situation A** — the Accepted B1 baseline explicitly approves the current structure. No migration is required.

### 3.3 Differences with Database Baseline Candidate

| Aspect | B1 Baseline (Accepted) | Candidate (Pre-approval) | Decision |
|--------|------------------------|--------------------------|----------|
| Time field | `created_at` | `occurred_at` | Keep `created_at`. Document it IS the event occurrence time. |
| Actor ID | `actor_id` | `actor_user_id` + `actor_public_link_id` | Keep `actor_id`. PublicLink support deferred. |
| Actor types | USER / SYSTEM / JOB | USER / CLIENT / SYSTEM / JOB | Keep B1 enum. CLIENT deferred to V0.2. |
| Source enum | API / INTERNAL / JOB / CRON | API / PUBLIC / WORKER / MIGRATION | Keep B1 enum. See mapping below. |
| request_id type | `varchar(36)` | `uuid` | Keep `varchar(36)`. See rationale below. |
| metadata nullability | `jsonb NULL` | `jsonb NOT NULL DEFAULT '{}'` | Keep nullable. See rationale below. |
| Target naming | `target_type` / `target_id` | `aggregate_type` / `aggregate_id` | Keep `target_*`. Consistent within B1. |
| Scope | Identity + workspace audit | Global unified audit | B1 scope. Evolution path documented. |

### 3.4 Decision Record

#### `created_at` vs `occurred_at`

B1 uses `created_at`. This field IS the event occurrence time — audit events are immutable facts recorded at the moment they occur. There is no separate "write-to-DB time" vs "event time" in the B1 architecture. The field name `created_at` is consistent with all other tables in the system.

If a future requirement arises to distinguish event generation time from DB write time, a new column can be added via migration.

#### `actor_id` naming

B1 uses `actor_id` as a generic FK to `app_user(id)`. When PublicLink / Client actors are introduced in V0.2+:
- A new column `actor_public_link_id` can be added.
- The `actor_type` CHECK can be extended to include `CLIENT`.
- `actor_id` continues to reference `app_user` for USER actors.
- `actor_public_link_id` (future) references the `public_link` table.

This is a non-breaking evolution via forward migration.

#### Source enum mapping

| B1 Source | Semantics | Future mapping |
|-----------|-----------|----------------|
| `API` | Request via HTTP API | Stays as `API` |
| `INTERNAL` | Internal application event | Maps to `WORKER` or stays as `INTERNAL` |
| `JOB` | Scheduled/background task | Maps to `WORKER` or stays as `JOB` |
| `CRON` | Cron-triggered task | Maps to `WORKER` or stays as `CRON` |

Actor type (`JOB`) represents **who** executes. Source represents **what entry point** triggered the event. These are orthogonal concepts and should not be merged.

Future decision: when introducing `PUBLIC` source (for public API access), add it to the CHECK constraint via migration. Do not preemptively add it.

#### `request_id` type: `varchar(36)` vs `uuid`

B1 uses `varchar(36)`. The `RequestIdFilter` generates only valid UUIDs and accepts incoming UUIDs only (rejects malformed values). However:
- The B1 baseline explicitly specifies `varchar(36)`.
- Changing to `uuid` is a tightening that should go through a formal migration + product decision.
- Recommendation for V0.2: consider a forward migration to `uuid` type.

#### `metadata` nullable vs `NOT NULL DEFAULT '{}'`

B1 uses `jsonb NULL`. Semantics:
- **NULL**: No metadata was provided for this event (common for simple system events).
- **`{}`**: Explicit empty metadata object was provided.
- **Non-empty object**: Metadata was provided.

Making it `NOT NULL DEFAULT '{}'` would require a data migration that triggers the append-only protection (UPDATE on `audit_event` is blocked by `fn_reject_audit_mutation()`). This can be done in a future migration with temporary trigger disablement, but is not justified in MF-BE-005A since B1 explicitly approved `jsonb NULL`.

#### Target/Aggregate naming

B1 uses `target_type` / `target_id`. This naming is consistent throughout all B1 API error codes, audit events, and tests. The alternative `aggregate_type` / `aggregate_id` is a DDD term not used elsewhere in the B1 baseline.

### 3.5 Future Evolution Path

| Change | When | How |
|--------|------|-----|
| Add `CLIENT` actor type | V0.2 (PublicLink) | ALTER CHECK + add `actor_public_link_id` column |
| Add `PUBLIC` source | V0.2 (Public API) | ALTER CHECK constraint |
| Rename `created_at` → `occurred_at` | If needed | Forward migration (requires audit trigger handling) |
| Change `request_id` to `uuid` | V0.2+ | Forward migration with data conversion |
| Make `metadata` NOT NULL | V0.2+ | Forward migration with trigger handling |
| Add `project_id` column | V0.2 (Project module) | ALTER TABLE + new FK |
| Add `aggregate_type` / `aggregate_id` | If DDD alignment needed | New columns, not rename of target_* |

### 3.6 MF-BE-006 ORM Mapping

MF-BE-006 Entity mapping for `audit_event` MUST use the B1 §16.6 field names:

```
id, actor_id, actor_type, action, target_type, target_id,
workspace_id, request_id, source, summary, metadata, created_at
```

No alternative field names from the Database Baseline Candidate shall be used in Entity mapping.

---

## 4. Migration Principles

| Principle | Enforcement |
|-----------|-------------|
| V001–V005 not modified | Git diff confirms zero changes to existing migrations |
| Only forward V* migrations | V006, V007 are pure forward migrations |
| No Down Migration | No R__ or U__ files created |
| No Flyway Repair | No checksum modification |
| No CASCADE | All DDL uses explicit names without CASCADE |
| No PostgreSQL ENUM | All CHECK constraints use varchar |
| No dynamic defaults | No `now()` as column default for expiry fields |

---

## 5. Test Coverage

| Test Class | New Tests | Verified SQLSTATEs |
|------------|-----------|--------------------|
| SchemaAlignmentMigrationIT | ~12 | — |
| AuthenticationIndexesIT | Updated: redundant index assertion | — |
| IdentityConstraintsIT | Updated: uses new column names | 23502, 23503, 23505, 23514 |
| AuthenticationSchemaMigrationIT | Updated: V006/V007 history, column structure | — |

---

## 6. Summary of Changes

| Migration | Purpose | Data Backfill | Result |
|-----------|---------|---------------|--------|
| V006 | Remove redundant `idx_auth_session_family` | None | Index dropped, unique constraint index preserved |
| V007 | Split `expires_at` into `access_expires_at` + `refresh_expires_at` | `access_expires_at = LEAST(created_at + 15min, refresh_expires_at)` | Two explicit expiry columns with time-order constraints |

No V008 migration. `audit_event` structure is per B1 §16.6 (Frozen, Accepted). Decision documented above.
