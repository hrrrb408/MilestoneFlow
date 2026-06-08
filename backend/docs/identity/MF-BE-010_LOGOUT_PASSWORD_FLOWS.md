# MF-BE-010: Logout, Password Change & Password Reset

**Status:** Complete
**Branch:** `feat/MF-BE-010-logout-password-management`
**Base:** `feat/MF-BE-009-refresh-token-rotation-final` (MF-BE-009 PR #2 open)
**Depends on:** MF-BE-009 (open PR #2)

---

## 1. Task Scope

Implement logout, logout-all, password change, forgot password, and password reset.

| Feature               | API                                 | Status       |
| --------------------- | ----------------------------------- | ------------ |
| Logout                | `POST /api/v1/auth/logout`          | Implemented  |
| Logout All            | `POST /api/v1/auth/logout-all`      | Implemented  |
| Password Change       | `POST /api/v1/auth/password/change` | Implemented  |
| Forgot Password       | `POST /api/v1/auth/password/forgot` | Implemented  |
| Reset Password        | `POST /api/v1/auth/password/reset`  | Implemented  |

**Explicitly NOT implemented:**

- Session list (`GET /api/v1/auth/sessions`)
- Revoke single session (`DELETE /api/v1/auth/sessions/{id}`)
- Workspace creation
- Redis rate limiting
- Real email provider
- JWT / OAuth2 / MFA / SSO
- Audit Java model
- Any database migration (V001–V007 unchanged)

---

## 2. Logout Flow

### 2.1 Single Logout

```
Authenticated request (MF_ACCESS cookie)
→ Resolve session ID from principal
→ Find session by ID (PESSIMISTIC_WRITE lock)
→ Validate session is ACTIVE
→ Revoke session (status=REVOKED, reason=LOGOUT, revokedAt=now)
→ Save session
→ Clear MF_ACCESS, MF_REFRESH, XSRF-TOKEN cookies
→ Return 204 No Content
```

Per B1 Baseline §14:
- Returns 204 (no body)
- Requires authentication
- Already-revoked session returns 401 AUTH_SESSION_REVOKED
- Session is not deleted from database

### 2.2 Logout All

```
Authenticated request (MF_ACCESS cookie)
→ Resolve user ID from principal
→ Bulk update: SET status=REVOKED, revokedAt=now, revokeReason=LOGOUT_ALL
  WHERE userId=? AND status=ACTIVE
→ Clear MF_ACCESS, MF_REFRESH, XSRF-TOKEN cookies
→ Return 204 No Content
```

Per B1 Baseline §14.2:
- Returns 204 (no body)
- Requires authentication
- Idempotent — succeeds even if no active sessions remain
- Other users' sessions are not affected

---

## 3. Password Change Flow

```
Authenticated request
→ Validate currentPassword and newPassword (Bean Validation)
→ Load user by ID
→ Verify user is not DISABLED
→ Verify currentPassword matches stored hash
→ Validate newPassword against PasswordPolicy
→ Encode newPassword
→ Update passwordHash on user
→ Save user
→ Revoke all ACTIVE sessions (PASSWORD_CHANGE reason)
→ Commit transaction
→ Clear cookies
→ Return 200 { data: { passwordChanged: true } }
```

Per B1 Baseline §3:
- Password change revokes ALL sessions for the user
- User must re-login after changing password
- Does not return tokens
- Does not auto-login

### Error Codes

| Code                             | HTTP | Scenario                        |
| -------------------------------- | ---: | ------------------------------- |
| `AUTH_INVALID_CREDENTIALS`       |  401 | Current password is wrong       |
| `AUTH_PASSWORD_POLICY_VIOLATION` |  422 | New password fails policy       |
| `AUTH_ACCOUNT_DISABLED`          |  401 | User account is disabled        |

---

## 4. Forgot Password Flow

```
POST /auth/password/forgot { email }
→ Normalize email
→ Find user by normalized email
→ If user not found: return 200 (no token, no email)
→ If user not ACTIVE: return 200 (no token, no email)
→ If user ACTIVE:
    → Generate raw reset token (256-bit, Base64URL)
    → Hash token (SHA-256)
    → Create VerificationToken (purpose=PASSWORD_RESET, hash, expiresAt)
    → Save token
    → Commit transaction
    → AFTER_COMMIT: publish PasswordResetRequestedEvent
    → Listener dispatches email via PasswordResetEmailSender
→ Return 200 { data: { accepted: true } }
```

Per B1 Baseline §9:
- Always returns 200 regardless of email existence (anti-enumeration)
- Token validity: 1 hour (configurable via `milestoneflow.auth.password-reset.token-ttl`)
- Multiple active tokens allowed per B1 §9.2
- Old PASSWORD_RESET tokens are NOT deleted when creating new ones
- EMAIL_VERIFICATION tokens are never affected

### Anti-Enumeration

| User State            | Response           | Token Created | Email Sent |
| --------------------- | ------------------ | ------------- | ---------- |
| Unknown email         | 200 { accepted: true } | No        | No         |
| PENDING_VERIFICATION  | 200 { accepted: true } | No        | No         |
| ACTIVE                | 200 { accepted: true } | Yes       | Yes        |
| DISABLED              | 200 { accepted: true } | No        | No         |

---

## 5. Reset Password Flow

```
POST /auth/password/reset { token, newPassword }
→ Validate request (Bean Validation)
→ Hash raw token (SHA-256)
→ Find PASSWORD_RESET token with PESSIMISTIC_WRITE lock
→ Validate: token exists, not used, not expired
→ Load user, verify ACTIVE
→ Validate newPassword against PasswordPolicy
→ Encode newPassword
→ Update passwordHash on user
→ Mark token usedAt = now
→ Revoke all ACTIVE sessions (PASSWORD_RESET reason)
→ Commit transaction
→ Clear cookies
→ Return 200 { data: { passwordReset: true } }
```

Per B1 Baseline §9:
- Token is single-use
- Concurrent reset with same token: only one succeeds (PESSIMISTIC_WRITE)
- All sessions revoked after successful reset
- Does not auto-login
- Does not return tokens

### Error Codes

| Code                                  | HTTP | Scenario                        |
| ------------------------------------- | ---: | ------------------------------- |
| `AUTH_PASSWORD_RESET_TOKEN_INVALID`   |  401 | Token not found or already used |
| `AUTH_PASSWORD_RESET_TOKEN_EXPIRED`   |  401 | Token has expired               |
| `AUTH_PASSWORD_POLICY_VIOLATION`      |  422 | New password fails policy       |
| `AUTH_ACCOUNT_DISABLED`               |  401 | User account is disabled        |

---

## 6. Token Hash Strategy

All security tokens (access, refresh, verification, reset) follow the same pattern:

1. **Raw token**: generated by `SecureTokenGenerator` (256-bit, Base64URL)
2. **Hash**: SHA-256 of raw token → 64-char lowercase hex string
3. **Database**: only the hash is stored
4. **Raw token**: only in memory, transmitted via cookie or email, never logged
5. **Lookup**: hash the presented token, find by hash

The `verification_token` table stores both `EMAIL_VERIFICATION` and `PASSWORD_RESET` tokens, differentiated by the `purpose` column. The `VerificationTokenPurpose` enum has values `EMAIL_VERIFICATION` and `PASSWORD_RESET`.

---

## 7. Configuration

```yaml
milestoneflow:
  auth:
    password-reset:
      token-ttl: PT1H    # Default: 1 hour (B1 §9.2)
```

- Uses `java.time.Duration`
- TTL > 0 validated at startup
- Not hardcoded in service
- Can be overridden via environment variable
- Tests use fixed Clock

---

## 8. Session Revocation Strategy

Per B1 Baseline §3 and §9:

| Event             | Sessions Revoked | Revoke Reason      |
| ----------------- | ---------------- | ------------------ |
| Logout            | Current session  | `LOGOUT`           |
| Logout All        | All ACTIVE       | `LOGOUT_ALL`       |
| Password Change   | All ACTIVE       | `PASSWORD_CHANGE`  |
| Password Reset    | All ACTIVE       | `PASSWORD_RESET`   |

Revoke reasons stored in `AuthSessionRevokeReason` constants:
- `REFRESH_ROTATED` — session superseded by refresh rotation
- `REFRESH_REPLAY_DETECTED` — family revoked due to replay
- `REFRESH_EXPIRED` — expired naturally
- `LOGOUT` — user initiated single logout
- `LOGOUT_ALL` — user initiated logout all
- `PASSWORD_CHANGE` — all sessions revoked on password change
- `PASSWORD_RESET` — all sessions revoked on password reset

---

## 9. Cookie Clearing Strategy

Per B1 Baseline §2 and §14:

| Scenario           | MF_ACCESS | MF_REFRESH | XSRF-TOKEN | Notes                          |
| ------------------ | --------- | ---------- | ---------- | ------------------------------ |
| Logout             | Cleared   | Cleared    | Cleared    | Max-Age=0                      |
| Logout All         | Cleared   | Cleared    | Cleared    | Max-Age=0                      |
| Password Change    | Cleared   | Cleared    | Cleared    | All sessions revoked           |
| Password Reset     | Cleared   | Cleared    | Cleared    | All sessions revoked           |

All three cookies are cleared using `AuthCookieWriter` methods:
- `buildClearAccessCookie()` — MF_ACCESS, Max-Age=0
- `buildClearRefreshCookie()` — MF_REFRESH, Max-Age=0
- `buildClearXsrfCookie()` — XSRF-TOKEN, Max-Age=0

---

## 10. Email Sender Port

### PasswordResetEmailSender

```java
public interface PasswordResetEmailSender {
    void send(String recipientEmail, String displayName, String rawToken, Locale locale);
}
```

### Noop Implementation

`NoopPasswordResetEmailSender` is activated only when:
- Profile is `local` or `test`
- Property `milestoneflow.email.provider=noop`

Production behavior: no bean created → application fails to start (fail-closed).

### Event Publishing

`PasswordResetRequestedEvent` is published within the `@Transactional` forgot-password method. The `PasswordResetRequestedListener` handles it with `@TransactionalEventListener(phase = AFTER_COMMIT)`:

- Email is sent only after transaction commits
- Email failure does not roll back the committed transaction
- User can request another forgot-password
- `toString()` redacts the raw token

---

## 11. Transaction Boundaries

Per B1 Baseline §17:

| Operation       | Transaction Scope                                                         |
| --------------- | ------------------------------------------------------------------------- |
| Logout          | Lock + revoke single session                                              |
| Logout All      | Bulk update all ACTIVE sessions                                           |
| Password Change | Verify password + update hash + revoke all sessions (single transaction)  |
| Forgot Password | Create reset token (single transaction). Email AFTER_COMMIT               |
| Reset Password  | Lock token + validate + update hash + mark used + revoke all (single tx)  |

---

## 12. Security Guarantees

- Current password is never logged
- New password is never logged
- Password hash never appears in API responses
- Raw reset token is never persisted to database
- Raw reset token never appears in logs
- Reset token hash never appears in API responses
- Cookie values never appear in logs
- Forgot password prevents account enumeration
- Reset token failure does not leak token state details
- Logout does not return session internals
- Password change does not return tokens
- Reset does not auto-login
- No JWT
- No Redis
- No Workspace

---

## 13. Test Coverage

### Unit Tests (52 new)

- `ChangePasswordServiceTest`: 10 tests — success, current password validation, new password policy, hash update, session revocation, disabled user
- `ForgotPasswordServiceTest`: 9 tests — ACTIVE user token creation, anti-enumeration, raw token not saved, email normalization, event redaction, multiple tokens
- `ResetPasswordServiceTest`: 14 tests — success, token validation, expiry, reuse, user status, password policy, session revocation, pessimistic lock
- `AuthPasswordControllerTest`: 19 tests — change (auth, cookies, errors), forgot (anti-enumeration, validation, no leak), reset (success, errors, cookies)
- `AuthLogoutControllerTest`: 5 tests (from MF-BE-010 Commit 1)
- `LogoutServiceTest`: 8 tests (from MF-BE-010 Commit 1)

### Integration Tests (PostgreSQL 17 Testcontainers)

- `LogoutFlowIT`: 7 tests — session revocation, cookie clearing, /me failure, refresh failure, cross-user isolation
- `PasswordChangeFlowIT`: 8 tests — change success, old password fails, new password works, sessions revoked, hash updated
- `ForgotPasswordFlowIT`: 6 tests — token creation, anti-enumeration, EMAIL_VERIFICATION unaffected
- `ResetPasswordFlowIT`: 7 tests — reset success, token reuse, expiry, password update, session revocation
- `PasswordResetConcurrencyIT`: 1 test — concurrent reset, one wins, no deadlock

### ArchUnit

All architecture rules pass (460 total tests including new ones).

---

## 14. Production Release Status

**Production release: NOT allowed.**

Blocked by:
- No real `VerificationEmailSender` implementation
- No real `PasswordResetEmailSender` implementation
- No rate limiting
- No security audit

---

## 15. MF-BE-010 Stacked Branch Note

```
MF-BE-010 is stacked on MF-BE-009
Merge order:
  MF-BE-009 PR #2 → main
  MF-BE-010 PR → main
```

MF-BE-009 PR #2 is currently OPEN. This branch must not be merged before MF-BE-009.
