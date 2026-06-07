# B1 User Authentication Implementation Baseline

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-06-07 |
| Status | **Frozen** |
| Based On | Architecture doc §03, ADR-BE-001~010 review, Database Model V0.1, Pilot MVP PRD |
| Blocks Until | Superseded by product decision or V0.2 architecture review |

---

## 1. Authentication Method

**Decision: Opaque Access/Refresh Tokens stored as HttpOnly Cookies.**

NOT JWT. Architecture doc §03 ADR-007 explicitly chose opaque tokens for:
- Immediate revocation capability (no JWT blacklist needed).
- Password reset invalidation (revoke all sessions in one operation).
- Simpler replay detection (token hash comparison).
- No token content exposure to client.

Token lifecycle:
1. Login validates email + password.
2. Server generates opaque Access Token (high-entropy random string).
3. Server generates opaque Refresh Token (high-entropy random string).
4. Both tokens are SHA-256 hashed. Only hashes stored in `auth_session` table.
5. Raw tokens set as HttpOnly cookies in response.
6. Access Token cookie expires in 15 minutes.
7. Refresh Token cookie expires in 30 days.

---

## 2. Token & Cookie Strategy

### 2.1 Access Token

| Property | Value |
|----------|-------|
| Cookie Name | `MF_ACCESS` |
| Type | Opaque random string (32 bytes, Base64URL) |
| HttpOnly | Yes |
| Secure | Yes |
| SameSite | Lax |
| Path | `/api/v1` |
| Max-Age | 900 (15 minutes) |
| Stored in DB | SHA-256 hash only |

The Access Token is validated on every request by:
1. Reading `MF_ACCESS` cookie.
2. Computing SHA-256 hash.
3. Looking up `auth_session` by `access_token_hash` where `status = ACTIVE` and `expires > now()`.
4. Loading user_id, workspace memberships.

### 2.2 Refresh Token

| Property | Value |
|----------|-------|
| Cookie Name | `MF_REFRESH` |
| Type | Opaque random string (32 bytes, Base64URL) |
| HttpOnly | Yes |
| Secure | Yes |
| SameSite | Strict |
| Path | `/api/v1/auth/refresh` |
| Max-Age | 2592000 (30 days) |
| Stored in DB | SHA-256 hash only |

Refresh Token path is scoped to `/api/v1/auth/refresh` to prevent browser from sending it to other endpoints.

### 2.3 CSRF Token

| Property | Value |
|----------|-------|
| Cookie Name | `XSRF-TOKEN` |
| HttpOnly | No (JavaScript must read it) |
| Secure | Yes |
| SameSite | Lax |
| Path | `/api/v1` |
| Header Name | `X-XSRF-TOKEN` |

CSRF protection required for all non-GET/HEAD/OPTIONS internal API requests.
Flow: Server sets `XSRF-TOKEN` cookie → Frontend reads cookie → Frontend sends `X-XSRF-TOKEN` header → Server validates.

Public APIs use CSRF or Nonce for confirmatory POST requests.

### 2.4 CORS Policy

- Production: Same-origin deployment (no CORS needed).
- Development: Explicit origin whitelist, no `*` with credentials.

---

## 3. Password Policy

| Property | Value |
|----------|-------|
| Encoder | Spring Security `DelegatingPasswordEncoder` (BCrypt default) |
| BCrypt rounds | 12 (default) |
| Minimum length | 8 |
| Maximum length | 128 |
| Trim whitespace | No (passwords may contain spaces) |
| Complexity rules | None for V0.1 (no mandatory uppercase/digit/special) |
| Common weak passwords | Not blocked in V0.1 |
| Unicode | Allowed (DelegatingPasswordEncoder handles UTF-8) |
| Password change revocation | Revokes ALL sessions for the user |
| Password reset revocation | Revokes ALL sessions for the user |
| Log prohibition | Never log password, password_hash, or password in error messages |

---

## 4. Email Normalization

| Property | Value |
|----------|-------|
| Raw email | Stored in `email` (original input) |
| Normalized email | Stored in `email_normalized` |
| Normalization | `trim()` + Unicode NFC normalize + `toLowerCase()` |
| Unique constraint | On `email_normalized` |
| Gmail dot/plus handling | NOT applied (no provider-specific rules) |

Example: `" User@Example.COM "` → raw `email`: `" User@Example.COM "`, `email_normalized`: `"user@example.com"`.

---

## 5. User State Machine

```
PENDING_VERIFICATION ──verifyEmail──→ ACTIVE
PENDING_VERIFICATION ──disable─────→ DISABLED
ACTIVE ──disable───────────────────→ DISABLED
DISABLED ──adminRestore────────────→ ACTIVE
```

| Status | Description |
|--------|-------------|
| `PENDING_VERIFICATION` | Registered but email not verified. Cannot login. |
| `ACTIVE` | Normal account. Can login. |
| `DISABLED` | Login prohibited. All sessions revoked. |

V0.1: `DISABLED → ACTIVE` transition exists but no admin UI. Reserved for manual DB intervention.

---

## 6. Session Model

### 6.1 AuthSession Table

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | uuid | NOT NULL | App generated | PK |
| user_id | uuid | NOT NULL | | FK → app_user(id) |
| access_token_hash | varchar(64) | NOT NULL | | SHA-256 hex of raw access token |
| refresh_token_hash | varchar(64) | NOT NULL | | SHA-256 hex of raw refresh token |
| session_family_id | uuid | NOT NULL | | Groups sessions from same login chain |
| refresh_generation | bigint | NOT NULL | 0 | Increments on each refresh |
| status | varchar(24) | NOT NULL | `ACTIVE` | ACTIVE / REVOKED / EXPIRED |
| user_agent | varchar(512) | YES | | Browser user agent (truncated) |
| ip_address | varchar(45) | YES | | Client IP (truncated) |
| created_at | timestamptz | NOT NULL | now() | |
| expires_at | timestamptz | NOT NULL | | Refresh token expiry (30 days from creation) |
| last_used_at | timestamptz | YES | | Last access token validation |
| revoked_at | timestamptz | YES | | When revoked |
| revoke_reason | varchar(48) | YES | | LOGOUT / PASSWORD_CHANGE / PASSWORD_RESET / ACCOUNT_DISABLE / REPLAY_DETECTED / LOGOUT_ALL |

**Constraints:**
- `UNIQUE(access_token_hash)` — only one active access token per hash.
- `UNIQUE(refresh_token_hash)` — only one active refresh token per hash.
- `INDEX(user_id, status)` — find active sessions for a user.
- `INDEX(session_family_id, refresh_generation)` — detect replay across family.

### 6.2 Session Family & Refresh Rotation

- Each login creates a new `session_family_id` with `refresh_generation = 0`.
- Refresh: new tokens issued, `refresh_generation` incremented, old token hash invalidated.
- `session_family_id` links all tokens from the same original login.
- Replay detection: if a previously-used refresh token hash is presented, the ENTIRE family is revoked (all generations).

---

## 7. Refresh Token Rotation & Replay Detection

### 7.1 Rotation Flow

1. Client sends `MF_REFRESH` cookie to `POST /api/v1/auth/refresh`.
2. Server computes SHA-256 of raw refresh token.
3. Looks up `auth_session` by `refresh_token_hash`.
4. If `status != ACTIVE` or `expires_at < now()`: return 401.
5. Generate new access + refresh tokens.
6. Compute new hashes.
7. **In the same transaction**:
   - Update `auth_session`: set `access_token_hash` = new, `refresh_token_hash` = new, `refresh_generation += 1`, `last_used_at = now()`.
   - Old refresh token hash is now invalid.
8. Set new cookies in response.

### 7.2 Replay Detection

If a client presents a refresh token hash that was previously valid but is no longer the current hash for this session:

1. Look up by `session_family_id` where `refresh_generation >= presented_generation`.
2. Revoke ALL sessions in this family (`status = REVOKED`, `revoke_reason = REPLAY_DETECTED`).
3. Return 401 `AUTH_REFRESH_REPLAY_DETECTED`.

This follows the OAuth 2.0 rotating refresh token best practice.

### 7.3 Concurrent Refresh

Two concurrent refresh requests for the same session:
- The first wins (updates the hash in DB).
- The second finds the old hash no longer matches → triggers replay detection → revokes entire family.
- **This is correct behavior.** Only one client should be refreshing at a time.
- Mitigation: Frontend should serialize refresh requests (only one in-flight at a time).

### 7.4 Cookie Update Failure

If the server updates the DB but the response (with Set-Cookie) fails to reach the client:
- Client still has old refresh token → triggers replay detection on next attempt → session family revoked.
- User must re-login. This is a security-first tradeoff.

---

## 8. Email Verification

### 8.1 Flow

1. User registers → account created with `status = PENDING_VERIFICATION` → verification token created.
2. Token: 32-byte random string, Base64URL encoded. DB stores only SHA-256 hash.
3. Token sent via email (async task, does not block registration response).
4. User clicks link → frontend sends `POST /api/v1/auth/email-verification/confirm` with token.
5. Server validates: hash matches, `expires_at > now()`, `used_at IS NULL`, `purpose = EMAIL_VERIFICATION`.
6. Success: `app_user.status → ACTIVE`, `email_verified_at = now()`, token `used_at = now()`.
7. Does NOT auto-login. User must login separately.

### 8.2 Rules

| Property | Value |
|----------|-------|
| Token validity | 24 hours |
| Single use | Yes. `used_at` set on first use. |
| Resend cooldown | 60 seconds between resends |
| Old token on resend | Previous token remains valid until expiry or used. Multiple valid tokens allowed. |
| Email send failure | Account still created (status PENDING_VERIFICATION). User can resend. |
| Already verified | Return 200 with informational message (not error). |
| Already used token | Return 401 `AUTH_VERIFICATION_TOKEN_EXPIRED`. |
| Prevents account enumeration | Yes. Resend endpoint does not reveal whether email exists. |

---

## 9. Password Reset

### 9.1 Flow

1. User submits `POST /api/v1/auth/password/forgot` with `{ email }`.
2. **Always returns 200** regardless of whether email exists. Prevents enumeration.
3. If email exists and account is ACTIVE: create reset token, send email.
4. Token: same mechanism as verification token (32-byte random, SHA-256 hash stored).
5. User clicks link → frontend sends `POST /api/v1/auth/password/reset` with token + new password.
6. Success: update password, revoke ALL sessions, token `used_at = now()`.

### 9.2 Rules

| Property | Value |
|----------|-------|
| Token validity | 1 hour |
| Single use | Yes |
| Rate limiting | Max 3 requests per email per hour |
| Multiple active tokens | Allowed. Each new request creates a new token; old tokens remain valid until expiry. |
| Session revocation | ALL sessions revoked on successful reset |
| Prevents enumeration | Forgot endpoint always returns 200. Reset endpoint returns standard token errors. |

---

## 10. Account Enumeration Prevention

| Endpoint | Strategy |
|----------|----------|
| `POST /auth/register` | Duplicate email → 409 `AUTH_EMAIL_ALREADY_EXISTS`. Enumeration possible here. Mitigated by rate limiting. |
| `POST /auth/login` | Wrong email or wrong password → identical 401 `AUTH_INVALID_CREDENTIALS`. No distinction. |
| `POST /auth/password/forgot` | Always returns 200. No error if email doesn't exist. |
| `POST /auth/email-verification/resend` | Always returns 200. No error if email doesn't exist. |

**Risk**: Registration reveals email existence. Accepted for V0.1. Mitigation: rate limit registration endpoint (max 5 per IP per hour).

---

## 11. Rate Limiting

### 11.1 V0.1 Strategy (No Redis)

Use in-memory `ConcurrentHashMap` + scheduled cleanup for rate limiting.

| Endpoint | Limit | Key | Window |
|----------|-------|-----|--------|
| `POST /auth/register` | 5 | IP address | 1 hour |
| `POST /auth/login` | 10 | email_normalized | 15 minutes |
| `POST /auth/email-verification/resend` | 3 | email_normalized | 1 hour |
| `POST /auth/password/forgot` | 3 | IP address | 1 hour |
| `POST /auth/refresh` | 20 | IP address | 1 minute |

### 11.2 Implementation

- Interface: `RateLimiter { boolean allow(String key, int maxAttempts, Duration window); }`
- V0.1: `InMemoryRateLimiter` with `ConcurrentHashMap<String, Deque<Instant>>`.
- V0.2+: Replace with Redis-backed implementation when Redis is introduced.
- Rate limited response: 429 `AUTH_RATE_LIMITED` with `Retry-After` header.

### 11.3 Login Failure Handling

- No account locking in V0.1.
- Progressive delay not implemented in V0.1 (would require state persistence).
- Rate limiting at the email dimension provides basic protection.
- Security events are logged for monitoring.

---

## 12. Workspace Creation Relationship

**Decision: User explicitly creates workspace AFTER email verification.**

Based on architecture doc §03 and user story US-WS-001.

### Registration → Workspace Flow

1. User registers → account `PENDING_VERIFICATION`.
2. User verifies email → account `ACTIVE`.
3. User logs in → receives access/refresh tokens. **No workspace exists yet.**
4. Frontend detects no workspace → prompts user to create one.
5. User submits workspace name, currency, timezone → `POST /api/v1/workspaces`.
6. Backend creates workspace + OWNER membership in single transaction.

### B1 Scope Decision

| Item | In B1? | Reason |
|------|--------|--------|
| User registration | **Yes** | Core auth flow |
| Email verification | **Yes** | Required before login |
| Login/logout/refresh | **Yes** | Core auth flow |
| Password forgot/reset | **Yes** | Required by PRD |
| Workspace creation | **No** | Separate module (B2). B1 creates only the `workspace` + `workspace_membership` tables so JPA validate passes. |
| Workspace management API | **No** | Deferred to B2 |

**Important**: B1 creates the `workspace` and `workspace_membership` tables via Flyway migration (because JPA validate requires them if entities exist), but does NOT implement any workspace Controller or Service. The workspace module is structurally present but functionally empty.

---

## 13. Module Boundary

```
Identity module (B1 scope):
  - User entity, password, verification token, auth session
  - Registration, login, logout, refresh, password reset
  - Session management, rate limiting
  - Security filter chain

Workspace module (B1 structural only):
  - Workspace + Membership tables exist
  - No controller, service, or business logic in B1

Identity does NOT:
  - Know about projects, milestones, or business entities
  - Handle workspace permissions or roles
  - Manage business data isolation

Workspace does NOT:
  - Validate passwords or manage tokens
```

---

## 14. API Baseline

### 14.1 P0 — Must Have in B1

| API | Method | Auth Required | Success | Key Error Codes | Idempotent | Rate Limited |
|-----|--------|---------------|---------|-----------------|------------|-------------|
| `/auth/register` | POST | No | 201 | `AUTH_EMAIL_ALREADY_EXISTS` (409), `VALIDATION_FAILED` (422) | No | 5/hr/IP |
| `/auth/email-verification/resend` | POST | No | 200 | `VALIDATION_FAILED` (422) | Yes (cooldown) | 3/hr/email |
| `/auth/email-verification/confirm` | POST | No | 200 | `AUTH_VERIFICATION_TOKEN_INVALID` (401), `AUTH_VERIFICATION_TOKEN_EXPIRED` (401) | Yes | 10/min/IP |
| `/auth/login` | POST | No | 200 | `AUTH_INVALID_CREDENTIALS` (401), `AUTH_EMAIL_NOT_VERIFIED` (403), `AUTH_ACCOUNT_DISABLED` (401) | No | 10/15min/email |
| `/auth/refresh` | POST | No (uses refresh cookie) | 200 | `AUTH_SESSION_REVOKED` (401), `AUTH_REFRESH_REPLAY_DETECTED` (401) | No | 20/min/IP |
| `/auth/logout` | POST | Yes | 204 | `AUTH_SESSION_REVOKED` (401) | Yes | No |
| `/auth/password/forgot` | POST | No | 200 | Always 200 (no enumeration) | Yes (cooldown) | 3/hr/IP |
| `/auth/password/reset` | POST | No | 200 | `AUTH_PASSWORD_RESET_TOKEN_INVALID` (401), `AUTH_PASSWORD_RESET_TOKEN_EXPIRED` (401), `AUTH_PASSWORD_POLICY_VIOLATION` (422) | No | 10/min/IP |
| `/auth/password/change` | POST | Yes | 200 | `AUTH_INVALID_CREDENTIALS` (401), `AUTH_PASSWORD_POLICY_VIOLATION` (422) | No | No |
| `/auth/me` | GET | Yes | 200 | `AUTH_SESSION_REVOKED` (401) | Yes | No |

### 14.2 P1 — Should Have in B1

| API | Method | Auth Required | Success | Key Error Codes |
|-----|--------|---------------|---------|-----------------|
| `/auth/logout-all` | POST | Yes | 204 | `AUTH_SESSION_REVOKED` (401) |
| `/auth/sessions` | GET | Yes | 200 | `AUTH_SESSION_REVOKED` (401) |
| `/auth/sessions/{sessionId}` | DELETE | Yes | 204 | `AUTH_SESSION_REVOKED` (401), `RESOURCE_NOT_FOUND` (404) |

### 14.3 Deferred to V0.2+

- Workspace CRUD APIs
- Member management
- Role changes
- Multi-device session details

### 14.4 API Request/Response Details

**POST /auth/register**
```
Request:  { "email": "...", "password": "...", "displayName": "..." }
Response: 201 { "data": { "id": "...", "email": "...", "status": "PENDING_VERIFICATION" }, "meta": { "requestId": "..." } }
Errors:   409 AUTH_EMAIL_ALREADY_EXISTS, 422 VALIDATION_FAILED
Audit:    USER_REGISTERED event
```

**POST /auth/login**
```
Request:  { "email": "...", "password": "..." }
Response: 200 { "data": { "id": "...", "email": "...", "status": "ACTIVE" }, "meta": { "requestId": "..." } }
          + Set-Cookie: MF_ACCESS=...; MF_REFRESH=...; XSRF-TOKEN=...
Errors:   401 AUTH_INVALID_CREDENTIALS, 403 AUTH_EMAIL_NOT_VERIFIED, 401 AUTH_ACCOUNT_DISABLED
Audit:    USER_LOGIN_SUCCESS or USER_LOGIN_FAILED event
```

**POST /auth/refresh**
```
Request:  (uses MF_REFRESH cookie)
Response: 200 { "data": { "id": "...", "email": "..." }, "meta": { "requestId": "..." } }
          + Set-Cookie: MF_ACCESS=...; MF_REFRESH=...; XSRF-TOKEN=...
Errors:   401 AUTH_SESSION_REVOKED, 401 AUTH_REFRESH_REPLAY_DETECTED
Audit:    SESSION_REFRESHED event
```

**POST /auth/logout**
```
Request:  (uses MF_ACCESS cookie)
Response: 204 (no body)
          + Clear-Cookie: MF_ACCESS; MF_REFRESH; XSRF-TOKEN
Errors:   401 AUTH_SESSION_REVOKED (already logged out)
Audit:    USER_LOGOUT event
```

---

## 15. Authentication Error Codes

| Code | HTTP | Description | Log Level | Audit Event |
|------|------|-------------|-----------|-------------|
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | Email already registered | INFO | USER_REGISTRATION_DUPLICATE |
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong email or password | WARN | USER_LOGIN_FAILED |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | Email not yet verified | INFO | USER_LOGIN_BLOCKED |
| `AUTH_ACCOUNT_DISABLED` | 401 | Account is disabled | WARN | USER_LOGIN_BLOCKED |
| `AUTH_ACCOUNT_LOCKED` | 401 | Account locked (future) | WARN | USER_LOGIN_BLOCKED |
| `AUTH_SESSION_REVOKED` | 401 | Session no longer valid | INFO | — |
| `AUTH_SESSION_EXPIRED` | 401 | Session expired | DEBUG | — |
| `AUTH_REFRESH_REPLAY_DETECTED` | 401 | Refresh token reuse detected | ERROR | SECURITY_REFRESH_REPLAY |
| `AUTH_VERIFICATION_TOKEN_INVALID` | 401 | Token not found or already used | INFO | — |
| `AUTH_VERIFICATION_TOKEN_EXPIRED` | 401 | Token has expired | INFO | — |
| `AUTH_PASSWORD_RESET_TOKEN_INVALID` | 401 | Token not found or already used | INFO | — |
| `AUTH_PASSWORD_RESET_TOKEN_EXPIRED` | 401 | Token has expired | INFO | — |
| `AUTH_PASSWORD_POLICY_VIOLATION` | 422 | Password doesn't meet requirements | INFO | — |
| `AUTH_RATE_LIMITED` | 429 | Too many requests | WARN | SECURITY_RATE_LIMITED |

**Rules**:
- Error codes are stable English uppercase. Frontend logic depends on `code`, not `message`.
- `message` is human-readable, may be displayed to users, but is NOT localized in V0.1.
- One error code maps to exactly one HTTP status.
- `AUTH_INVALID_CREDENTIALS` uses identical message for wrong email and wrong password.

---

## 16. Database Tables for B1

### 16.1 app_user

```sql
CREATE TABLE app_user (
    id              uuid            NOT NULL,
    email           varchar(320)    NOT NULL,
    email_normalized varchar(320)   NOT NULL,
    display_name    varchar(100)    NOT NULL,
    password_hash   varchar(255)    NOT NULL,
    status          varchar(32)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    locale          varchar(16)     NOT NULL DEFAULT 'zh-TW',
    email_verified_at timestamptz   NULL,
    last_login_at   timestamptz     NULL,
    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      uuid            NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      uuid            NULL,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_app_user_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_app_user_status ON app_user (status);
```

**Notes**:
- `created_by` is NULL for self-registration (no authenticated user exists yet).
- `email_verified_at` is NULL until verification completes.
- `password_changed_at` is NOT in V0.1 (password change is captured by `updated_at`).

### 16.2 auth_session

```sql
CREATE TABLE auth_session (
    id                  uuid            NOT NULL,
    user_id             uuid            NOT NULL,
    access_token_hash   varchar(64)     NOT NULL,
    refresh_token_hash  varchar(64)     NOT NULL,
    session_family_id   uuid            NOT NULL,
    refresh_generation  bigint          NOT NULL DEFAULT 0,
    status              varchar(24)     NOT NULL DEFAULT 'ACTIVE',
    user_agent          varchar(512)    NULL,
    ip_address          varchar(45)     NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    expires_at          timestamptz     NOT NULL,
    last_used_at        timestamptz     NULL,
    revoked_at          timestamptz     NULL,
    revoke_reason       varchar(48)     NULL,

    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT uq_auth_session_access_hash UNIQUE (access_token_hash),
    CONSTRAINT uq_auth_session_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_auth_session_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_auth_session_user_status ON auth_session (user_id, status);
CREATE INDEX idx_auth_session_family ON auth_session (session_family_id, refresh_generation);
```

### 16.3 verification_token

```sql
CREATE TABLE verification_token (
    id              uuid            NOT NULL,
    user_id         uuid            NOT NULL,
    purpose         varchar(48)     NOT NULL,
    token_hash      varchar(64)     NOT NULL,
    expires_at      timestamptz     NOT NULL,
    used_at         timestamptz     NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_verification_token PRIMARY KEY (id),
    CONSTRAINT fk_verification_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_verification_token_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_verification_token_lookup ON verification_token (token_hash, purpose)
    WHERE used_at IS NULL;
```

**Notes**:
- Single table for both email verification and password reset tokens (differentiated by `purpose`).
- Partial index on `(token_hash, purpose) WHERE used_at IS NULL` — only indexes valid tokens.
- Multiple active tokens per user per purpose are allowed.

### 16.4 workspace (structural only in B1)

```sql
CREATE TABLE workspace (
    id                  uuid            NOT NULL,
    name                varchar(120)    NOT NULL,
    slug                varchar(80)     NOT NULL,
    default_currency    char(3)         NOT NULL DEFAULT 'TWD',
    timezone            varchar(64)     NOT NULL DEFAULT 'Asia/Taipei',
    status              varchar(24)     NOT NULL DEFAULT 'ACTIVE',
    settings            jsonb           NOT NULL DEFAULT '{}',
    archived_at         timestamptz     NULL,
    archived_by         uuid            NULL,
    version             bigint          NOT NULL DEFAULT 0,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    created_by          uuid            NULL,
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    updated_by          uuid            NULL,

    CONSTRAINT pk_workspace PRIMARY KEY (id),
    CONSTRAINT uq_workspace_slug UNIQUE (slug),
    CONSTRAINT ck_workspace_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);
```

### 16.5 workspace_membership (structural only in B1)

```sql
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
    CONSTRAINT uq_workspace_membership UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_membership_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER')),
    CONSTRAINT ck_membership_status CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED'))
);

CREATE INDEX idx_membership_user ON workspace_membership (user_id, status);
```

**Notes**:
- V0.1 role CHECK only includes `OWNER`. Additional roles added in V0.2 via Flyway migration.
- `workspace_id` is NOT NULL. Workspace is a tenant root table, exempt from the composite FK pattern.

### 16.6 audit_event (basic structure in B1)

```sql
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

    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_audit_event_actor ON audit_event (actor_id, created_at DESC);
CREATE INDEX idx_audit_event_target ON audit_event (target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_event_workspace ON audit_event (workspace_id, created_at DESC);
```

**Metadata prohibition**: `metadata` JSONB must NEVER contain:
- `password`, `password_hash`, or any password-adjacent field.
- Raw token values (access, refresh, verification, reset).
- Cookie values.
- Complete request bodies.
- Password reset links.

---

## 17. Transaction Boundaries

| Operation | Transaction Scope |
|-----------|------------------|
| Registration | INSERT app_user + INSERT verification_token (single transaction). Email task queued async. |
| Email verification | UPDATE app_user.status + UPDATE verification_token.used_at (single transaction). |
| Login | INSERT auth_session (single transaction). |
| Refresh | UPDATE auth_session (token hashes + generation) (single transaction). |
| Replay detection | SELECT + UPDATE all family sessions (single transaction, row-level lock). |
| Logout | UPDATE auth_session.status = REVOKED (single transaction). |
| Logout all | UPDATE all ACTIVE sessions for user (single transaction). |
| Password change | UPDATE app_user.password_hash + UPDATE all sessions REVOKED (single transaction). |
| Password reset | UPDATE app_user.password_hash + UPDATE verification_token.used_at + UPDATE all sessions REVOKED (single transaction). |
| Account disable | UPDATE app_user.status + UPDATE all sessions REVOKED (single transaction). |

---

## 18. Security Boundaries

### 18.1 Spring Security Configuration

- Yes, Spring Security is used in B1. No custom filter chains bypassing it.
- `SecurityFilterChain`: stateless session management (no HTTP session), cookie-based auth.
- Custom `AuthenticationFilter` reads `MF_ACCESS` cookie, validates token hash against DB.
- `AuthenticationProvider`: loads user from DB, validates account status.
- `UserDetails`: internal adapter over `app_user` entity (not exposed to API).
- Method security: `@PreAuthorize` not used in B1 (no workspace roles yet).
- CORS: configured via `CorsConfigurationSource`.
- CSRF: `CookieCsrfTokenRepository` with `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header.
- Password encoder: `DelegatingPasswordEncoder` with BCrypt default.
- Security context: `SecurityContextHolder` with `MODE_INHERITABLETHREADLOCAL` for async.

### 18.2 Filter Chain Order

```
RequestIdFilter (existing)
→ CorsFilter
→ CsrfFilter
→ AuthenticationFilter (reads MF_ACCESS cookie, validates against DB)
→ ExceptionTranslationFilter (401/403 handling)
→ AuthorizationFilter
→ Controller
```

### 18.3 Authentication Exception Handling

- `AuthenticationEntryPoint`: Returns 401 `ApiErrorResponse` for unauthenticated requests to protected endpoints.
- `AccessDeniedHandler`: Returns 403 `ApiErrorResponse`.
- Both integrate with existing `GlobalExceptionHandler` and include `requestId`.

---

## 19. Test Baseline

### 19.1 Unit Tests (minimum)

| Test | Covers |
|------|--------|
| Email normalization | trim + NFC + toLowerCase |
| Password policy | min 8, max 128, no trim |
| User state machine | PENDING_VERIFICATION → ACTIVE → DISABLED → ACTIVE |
| Token generation | 32-byte random, uniqueness |
| Token hashing | SHA-256 of raw token matches stored hash |
| Token expiry | expired tokens rejected |
| Refresh rotation | generation increments, old hash invalidated |
| Replay detection | family revoked on reuse |
| Session revocation | status → REVOKED, revoke_reason set |
| Password change revokes sessions | all sessions revoked |
| Error code mapping | code ↔ HTTP status, one-to-one |
| IdGenerator interface | UUID v7 generation, test injection |
| Clock injection | fixed time in tests |
| AuditorAware | extracts user ID from security context |
| Rate limiter | allows N, blocks N+1 |

### 19.2 Repository Integration Tests (PostgreSQL Testcontainers)

| Test | Covers |
|------|--------|
| email_normalized unique | duplicate insert fails |
| Token hash unique | duplicate token hash fails |
| Session family query | find all by family_id |
| Status CHECK | invalid status rejected |
| FK constraints | user_id must exist |
| Optimistic lock | concurrent update throws exception |
| Expired token query | only returns unexpired tokens |
| Concurrent refresh | only one wins, other triggers replay |
| Flyway empty migration | all tables created |
| JPA validate | entity mappings match schema |

### 19.3 API Integration Tests

| Test | Covers |
|------|--------|
| Register success | 201, user created, verification token created |
| Duplicate email | 409 AUTH_EMAIL_ALREADY_EXISTS |
| Malformed JSON | 400 INVALID_REQUEST |
| Bean Validation fail | 422 VALIDATION_FAILED |
| Login success | 200, cookies set |
| Wrong password | 401 AUTH_INVALID_CREDENTIALS |
| Unverified email | 403 AUTH_EMAIL_NOT_VERIFIED |
| Disabled account | 401 AUTH_ACCOUNT_DISABLED |
| Refresh success | 200, new cookies |
| Refresh replay | 401, family revoked |
| Logout | 204, cookies cleared |
| Already revoked session | 401 |
| Forgot password | always 200 |
| Reset token expired | 401 |
| RequestId header + body match | both present, same value |
| Status code mapping | 401/403/409/422 correct |

### 19.4 Security Tests

| Test | Covers |
|------|--------|
| Password not in response | response body不含 password/password_hash |
| Password not in logs | log appender captures no password fields |
| Token raw not in DB | only SHA-256 hash stored |
| Cookie Secure flag | MF_ACCESS, MF_REFRESH, XSRF-TOKEN all Secure |
| Cookie HttpOnly flag | MF_ACCESS, MF_REFRESH HttpOnly; XSRF-TOKEN not |
| Cookie SameSite | MF_ACCESS=Lax, MF_REFRESH=Strict, XSRF-TOKEN=Lax |
| CORS policy | no wildcard with credentials |
| Account enumeration | login identical for wrong email and wrong password |
| Concurrent refresh safety | no duplicate sessions |
| Revoked token | 401 on any request |
| Disabled user | all sessions revoked, login blocked |
| Sensitive exception hidden | 500 responses contain no stack trace, SQL, class names |

---

## 20. Open Questions

### OPEN-Q-001: UUID v7 Library

- **Status**: Library not yet chosen.
- **Impact**: B1 entity ID generation.
- **Recommended**: `java-uuid-generator` by Toshiaki Maki (Apache 2.0).
- **Decision window**: Before MF-BE-006.
- **Blocks B1**: No. `IdGenerator` interface decouples choice.

### OPEN-Q-002: Workspace Creation Timing

- **Status**: Architecture doc indicates user explicitly creates workspace after verification. Registration does NOT auto-create.
- **Impact**: B1 migration scope. B1 creates workspace tables but no workspace logic.
- **Recommended**: Follow architecture doc. Workspace creation is B2.
- **Decision window**: Before MF-BE-005.
- **Blocks B1**: No. B1 only needs the tables.

---

## 21. Flyway Migration Sequence

| Migration | Content |
|-----------|---------|
| V001 | Bootstrap (already exists) |
| V002 | `app_user` + `verification_token` + `auth_session` tables |
| V003 | `workspace` + `workspace_membership` tables |
| V004 | `audit_event` table |
| V005 | Authentication indexes and constraints |

V002–V005 are created in B1. SQL content is NOT written in this architecture task — only the migration file naming and scope are frozen here.
