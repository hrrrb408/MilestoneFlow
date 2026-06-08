# MF-BE-009: Refresh Token Rotation & Replay Detection

**Status:** Complete
**Branch:** `feat/MF-BE-009-refresh-token-rotation-final`
**Base:** `main` (MF-BE-008 merged via PR #1)
**Depends on:** MF-BE-008 (merged)

---

## 1. Task Scope

Implement refresh token rotation, replay detection, and session family security revocation.

| Feature               | API                      | Status       |
| --------------------- | ------------------------ | ------------ |
| Refresh Token Rotation | `POST /api/v1/auth/refresh` | Implemented |
| Replay Detection      | (internal)               | Implemented  |
| Session Family Revoke | (internal)               | Implemented  |

**Explicitly NOT implemented:**

- Logout (`POST /api/v1/auth/logout`)
- Logout-all (`POST /api/v1/auth/logout-all`)
- Session list (`GET /api/v1/auth/sessions`)
- Password reset / change
- Workspace creation
- Redis rate limiting
- JWT
- OAuth2 / MFA / SSO
- Any database migration (V001–V007 unchanged)

---

## 2. Refresh Token Rotation Design

### Strategy

Each successful refresh creates a **new `AuthSession` row** in the same family, rather than updating the existing row. This preserves history for replay detection and audit.

**Per-refresh lifecycle:**

1. Lock current `AuthSession` row (`PESSIMISTIC_WRITE`)
2. Validate session status and expiration
3. Mark current session `REVOKED` with reason `REFRESH_ROTATED`
4. Create new `AuthSession` with:
   - Same `session_family_id`
   - `refresh_generation = current + 1`
   - New `access_token_hash` and `refresh_token_hash`
   - New expiration timestamps
   - Status `ACTIVE`
5. Commit both saves atomically
6. Write new cookies to response

This design leverages the existing unique constraint:
```sql
uk_auth_session_family_gen(session_family_id, refresh_generation)
```

---

## 3. Session Family

A **session family** groups all sessions created from a single login event. All refreshed sessions share the same `session_family_id`.

- Login creates a family with `refresh_generation = 0`
- Each refresh increments generation by 1
- Replay detection revokes ALL active sessions in the family

---

## 4. Generation Rule

| Event         | Generation |
|---------------|------------|
| Login         | 0          |
| 1st refresh   | 1          |
| 2nd refresh   | 2          |
| Nth refresh   | N          |

Generation is stored as `bigint NOT NULL CHECK (refresh_generation >= 0)`.

---

## 5. Replay Detection

When an already-rotated refresh token is reused:

1. Hash the raw refresh token
2. Find session via `findByRefreshTokenHashForUpdate` (locked)
3. Detect: session is `REVOKED` with reason `REFRESH_ROTATED`
4. Query all `ACTIVE` sessions in the same family
5. Mark each as `REVOKED` with reason `REFRESH_REPLAY_DETECTED`
6. Commit
7. Clear cookies
8. Return `401 AUTH_REFRESH_TOKEN_REUSED`

**Key properties:**
- No new tokens are issued on replay
- The entire family is revoked (including the new session from a valid refresh)
- Replay is idempotent: calling it again on an already-replayed session is a no-op

---

## 6. Successful Refresh Flow

```
Cookie: MF_REFRESH=<raw-token>
→ Hash raw token
→ findByRefreshTokenHashForUpdate (PESSIMISTIC_WRITE)
→ Session found
→ Session ACTIVE
→ Refresh not expired
→ User ACTIVE
→ Revoke old session (REFRESH_ROTATED)
→ Generate new raw access + refresh tokens
→ Hash new tokens
→ Create new AuthSession (same family, generation + 1)
→ Save both sessions
→ Commit transaction
→ Set-Cookie: MF_ACCESS=<new-access>
→ Set-Cookie: MF_REFRESH=<new-refresh>
→ Response: { data: { authenticated: true } }
```

**Transaction boundary:** Steps 1–10 are in a single `@Transactional` method. Cookies are only written after transaction commit.

---

## 7. Failed Refresh Flow

| Scenario                          | HTTP | Code                       | Cookies Cleared |
| --------------------------------- | ---: | -------------------------- | --------------- |
| No `MF_REFRESH` cookie            |  401 | `AUTH_UNAUTHENTICATED`     | No              |
| Token hash not found              |  401 | `AUTH_REFRESH_TOKEN_INVALID` | Yes          |
| Session `EXPIRED`                 |  401 | `AUTH_REFRESH_TOKEN_EXPIRED` | Yes          |
| Refresh token expired (time)      |  401 | `AUTH_REFRESH_TOKEN_EXPIRED` | Yes          |
| Session `REVOKED` (non-rotated)   |  401 | `AUTH_SESSION_REVOKED`     | Yes             |
| Token replayed (rotated + reused) |  401 | `AUTH_REFRESH_TOKEN_REUSED` | Yes          |
| User `DISABLED`                   |  401 | `AUTH_ACCOUNT_DISABLED`    | Yes             |
| User not found                    |  401 | `AUTH_REFRESH_TOKEN_INVALID` | Yes          |
| User `PENDING_VERIFICATION`       |  401 | `AUTH_REFRESH_TOKEN_INVALID` | Yes          |

**Error responses do not leak:**
- Token hashes
- Session state details
- User existence
- Database internals

---

## 8. Concurrent Refresh Behavior

**Policy:** Strict security (per B1 Baseline)

When two requests use the same refresh token concurrently:

1. **First request** acquires the `PESSIMISTIC_WRITE` lock:
   - Rotates the session successfully
   - Old session → `REVOKED(REFRESH_ROTATED)`
   - New session → `ACTIVE(generation + 1)`

2. **Second request** acquires the lock after the first commits:
   - Sees the old session is `REVOKED(REFRESH_ROTATED)`
   - Identifies this as a replay
   - Revokes the ENTIRE family, including the new session from request 1
   - Returns `AUTH_REFRESH_TOKEN_REUSED`

3. **Final state:** No `ACTIVE` sessions in the family. User must re-login.

**Properties:**
- No deadlocks (single-row lock per family)
- No sleep synchronization in tests
- Deterministic outcome regardless of timing

---

## 9. Cookie Strategy

### Refresh Success

| Cookie       | Value         | HttpOnly | SameSite | Path                    | Max-Age          |
| ------------ | ------------- | -------- | -------- | ----------------------- | ---------------- |
| `MF_ACCESS`  | New raw token | Yes      | Lax      | `/api/v1`               | Access TTL (15m) |
| `MF_REFRESH` | New raw token | Yes      | Strict   | `/api/v1/auth/refresh`  | Refresh TTL (30d)|

### Refresh Failure — Cookie Clearing

| Scenario      | `MF_ACCESS` | `MF_REFRESH` | `XSRF-TOKEN` |
| ------------- | ----------- | ------------ | ------------- |
| Token invalid | Cleared     | Cleared      | Unchanged     |
| Token expired | Cleared     | Cleared      | Unchanged     |
| Replay        | Cleared     | Cleared      | Unchanged     |
| Session revoked | Cleared   | Cleared      | Unchanged     |

### Cookie Clearing Mechanism

`AuthCookieWriter` provides `buildClearAccessCookie()` and `buildClearRefreshCookie()` which set:
- Same name and path as the original cookie
- `Max-Age=0`
- Empty value

**XSRF-TOKEN:** Not rotated during refresh per current policy. B1 Baseline does not mandate XSRF rotation on refresh.

---

## 10. Database Field Usage

All fields used from `auth_session` table (V002 + V007):

| Field                  | Usage                                          |
| ---------------------- | ---------------------------------------------- |
| `id`                   | Primary key                                    |
| `user_id`              | FK to `app_user`, loaded for status check      |
| `access_token_hash`    | SHA-256 hash (64-char hex)                     |
| `refresh_token_hash`   | SHA-256 hash (64-char hex), locked on refresh  |
| `session_family_id`    | Groups sessions from same login                |
| `refresh_generation`   | Incremented on each rotation                   |
| `status`               | `ACTIVE`, `REVOKED`, or `EXPIRED`              |
| `access_expires_at`    | Access token TTL boundary                      |
| `refresh_expires_at`   | Refresh token TTL boundary                     |
| `revoked_at`           | Set when session revoked                       |
| `revoke_reason`        | `REFRESH_ROTATED`, `REFRESH_REPLAY_DETECTED`, etc. |

**Raw tokens are never stored. Only SHA-256 hashes.**

---

## 11. Repository Lock

`findByRefreshTokenHashForUpdate` uses JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` which translates to `SELECT ... FOR UPDATE` in PostgreSQL.

This ensures:
- Only one refresh can process a given session at a time
- The locked row is not modified by concurrent transactions
- Replay detection works correctly under concurrency

The lock is encapsulated in the Spring Data repository and not exposed in the application port interface.

---

## 12. Transaction Boundary

### Refresh Success Transaction

```
@Transactional
├── Hash raw token
├── findByRefreshTokenHashForUpdate (lock)
├── Validate session
├── Validate user
├── Revoke old session
├── Save old session
├── Create new session
├── Save new session
└── Commit
```

### Replay Transaction

```
@Transactional
├── Hash raw token
├── findByRefreshTokenHashForUpdate (lock)
├── Detect replay (REVOKED + REFRESH_ROTATED)
├── Query active family sessions
├── Revoke each as REFRESH_REPLAY_DETECTED
├── Save all
└── Commit
```

If a transaction fails, no partial state is persisted. Cookies are only written after successful commit.

---

## 13. Error Codes

| Code                           | HTTP | Scenario                                   | Leaks Details |
| ------------------------------ | ---: | ------------------------------------------ | ------------- |
| `AUTH_UNAUTHENTICATED`         |  401 | No refresh cookie                          | No            |
| `AUTH_REFRESH_TOKEN_INVALID`   |  401 | Token not found / user not found           | No            |
| `AUTH_REFRESH_TOKEN_EXPIRED`   |  401 | Refresh TTL exceeded                       | No            |
| `AUTH_REFRESH_TOKEN_REUSED`    |  401 | Replayed rotated token                     | No            |
| `AUTH_SESSION_REVOKED`         |  401 | Session revoked for non-rotation reason    | No            |
| `AUTH_ACCOUNT_DISABLED`        |  401 | User account is disabled                   | No            |

---

## 14. Security Guarantees

- Raw access tokens are never persisted to the database
- Raw refresh tokens are never persisted to the database
- Token hashes never appear in API responses
- Cookie values never appear in logs
- Replay response does not leak session state
- Invalid token response does not leak token existence
- Refresh endpoint does not accept body tokens
- Refresh endpoint does not accept query string tokens
- Refresh endpoint does not accept Authorization header tokens
- Refresh cookie (`MF_REFRESH`) cannot authenticate `/auth/me`
- Old access token fails after family replay revocation
- No JWT, no Redis, no logout endpoint
- PESSIMISTIC_WRITE lock prevents TOCTOU races

---

## 15. Test Coverage

### Unit Tests (396 total, 60 new)

- `AuthSessionTest`: 15 new tests for `revokeAsRotated`, `revokeAsReplayDetected`, `isRefreshRotated`
- `RefreshTokenServiceTest`: 26 tests covering all refresh scenarios
- `AuthRefreshControllerTest`: 13 tests for controller behavior
- `IdentityExceptionHandlerTest`: 5 new tests for refresh exception handlers

### Integration Tests (3 new IT classes, PostgreSQL 17)

- `RefreshTokenFlowIT`: 15 tests — success, DB state, cookies, errors, security
- `RefreshTokenReplayIT`: 5 tests — replay detection, family revocation, /me after replay
- `RefreshTokenConcurrencyIT`: 2 tests — concurrent refresh with CountDownLatch, deadlock

### ArchUnit

All 11 architecture rules pass, including:
- Controllers must not depend on `..domain..` package
- Repository types must not reside in API layer

---

## 16. Production Release Status

**Production release: NOT allowed.**

Blocked by:
- No real `VerificationEmailSender` implementation
- No Logout (`/auth/logout`, `/auth/logout-all`)
- No Password Reset / Password Change
- No security audit or rate limiting

---

## 17. MF-BE-010 Input

The following are prerequisites for MF-BE-010 (Logout, Password Change, Password Reset):

1. `POST /api/v1/auth/logout` — revoke current session
2. `POST /api/v1/auth/logout-all` — revoke all user sessions
3. `GET /api/v1/auth/sessions` — list active sessions
4. `DELETE /api/v1/auth/sessions/{id}` — revoke specific session
5. `POST /api/v1/auth/password/change` — change password (authenticated)
6. `POST /api/v1/auth/password/forgot` — request password reset
7. `POST /api/v1/auth/password/reset` — reset password with token

MF-BE-009 provides:
- Stable `AuthSession` entity with revoke behavior
- `AuthSessionRevokeReason` constants
- Repository methods for family queries and locked lookups
- Cookie clearing infrastructure
- Exception handling patterns

---

## 18. Commit History

```
ff55e7f feat(backend): add refresh session locking and revoke reasons
e3b14aa feat(backend): implement refresh token rotation
7237326 feat(backend): expose refresh token API
b101b33 test(backend): verify refresh rotation and replay detection
```
