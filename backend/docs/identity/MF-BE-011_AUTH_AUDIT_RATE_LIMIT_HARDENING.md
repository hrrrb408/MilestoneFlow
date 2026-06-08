# MF-BE-011: Authentication Audit, Rate Limiting & Security Hardening

## Status: COMPLETE

**Branch:** `feat/MF-BE-011-auth-audit-rate-limit-hardening`
**Stacked on:** `feat/MF-BE-010-logout-password-management-final`
**Merge order:** MF-BE-010 PR #3 → main, then MF-BE-011 PR → main

---

## 1. Scope

This milestone implements B1 authentication security closure:

- Authentication audit event writing
- In-memory rate limiting for auth endpoints
- Login failure protection
- Email verification resend rate limiting
- Forgot password rate limiting
- Password reset rate limiting
- Security response headers
- Sensitive data protection verification

**Not implemented (explicitly deferred):**

- Real VerificationEmailSender
- Real PasswordResetEmailSender
- Redis distributed rate limiting
- External WAF
- OAuth2 / MFA / SSO
- Workspace creation / permissions
- Session list / revoke single session
- Audit query API / admin dashboard
- Production deployment

---

## 2. Audit Event Catalogue

### 2.1 Success Events

| Action | Trigger | Actor | Target |
|--------|---------|-------|--------|
| `AUTH_REGISTER_SUCCEEDED` | User registration completed | USER | app_user |
| `AUTH_EMAIL_VERIFICATION_CONFIRMED` | Email verified | USER | app_user |
| `AUTH_EMAIL_VERIFICATION_RESEND_REQUESTED` | Resend requested | USER | app_user |
| `AUTH_LOGIN_SUCCEEDED` | Login completed | USER | auth_session |
| `AUTH_REFRESH_SUCCEEDED` | Token refreshed | USER | auth_session |
| `AUTH_LOGOUT_SUCCEEDED` | Logout completed | USER | auth_session |
| `AUTH_LOGOUT_ALL_SUCCEEDED` | All sessions revoked | USER | app_user |
| `AUTH_PASSWORD_CHANGED` | Password changed | USER | app_user |
| `AUTH_PASSWORD_RESET_REQUESTED` | Reset token created | USER | app_user |
| `AUTH_PASSWORD_RESET_SUCCEEDED` | Password reset completed | USER | app_user |

### 2.2 Failure / Security Events

| Action | Trigger | Actor | Target |
|--------|---------|-------|--------|
| `AUTH_LOGIN_FAILED` | Login failed (any reason) | SYSTEM/USER | app_user |
| `AUTH_REFRESH_FAILED` | Refresh failed | SYSTEM | auth_session |
| `AUTH_REFRESH_REPLAY_DETECTED` | Refresh token replay | USER | auth_session |
| `AUTH_EMAIL_VERIFICATION_FAILED` | Token invalid/expired | — | — |
| `AUTH_PASSWORD_CHANGE_FAILED` | Wrong current password | USER | app_user |
| `AUTH_PASSWORD_RESET_FAILED` | Token invalid/expired | — | — |
| `AUTH_RATE_LIMIT_REJECTED` | Rate limit exceeded | SYSTEM | — |

---

## 3. Audit Event Field Mapping

Maps to `audit_event` table (V004__audit.sql):

| Java Field | Column | Type | Nullable | Notes |
|------------|--------|------|----------|-------|
| id | id | uuid | NOT NULL | UUID v7, client-generated |
| actorId | actor_id | uuid | NULL | USER type requires non-null |
| actorType | actor_type | varchar(24) | NOT NULL | USER / SYSTEM / JOB |
| action | action | varchar(64) | NOT NULL | Event identifier |
| targetType | target_type | varchar(48) | NULL | Entity type |
| targetId | target_id | uuid | NULL | Entity ID |
| workspaceId | workspace_id | uuid | NULL | Always null for identity events |
| requestId | request_id | varchar(36) | NULL | From X-Request-Id / MDC |
| source | source | varchar(24) | NOT NULL | Always "API" for auth events |
| summary | summary | varchar(500) | NOT NULL | Human-readable |
| metadata | metadata | jsonb | NULL | Key-value context |
| createdAt | created_at | timestamptz | NOT NULL | Event timestamp |

### Append-only enforcement

- Database triggers reject UPDATE and DELETE on `audit_event`
- Java repository only exposes `save()`
- No update or delete methods on `AuditEventRepository`

---

## 4. Metadata Sanitization Rules

### 4.1 Forbidden keys (never stored)

```
password, passwordHash, rawToken, tokenHash, cookie,
authorization, resetToken, verificationToken,
refreshToken, accessToken
```

Validation is case-insensitive. The `AuditEvent` constructor rejects any metadata map containing these keys.

### 4.2 Allowed keys

```
reasonCode, result, ipMasked, userAgentHash,
userAgentFamily, rateLimitKeyHash, sessionFamilyId
```

### 4.3 Email handling

- Full email is never stored in audit metadata
- Use `emailNormalizedHash` if cross-referencing is needed
- Login failure events do not record which email was used

---

## 5. Rate Limiting Policies

| Action | Key | Max Attempts | Window |
|--------|-----|-------------|--------|
| LOGIN | `login:<hash(normalizedEmail)>` | 5 failures | 15 minutes |
| REGISTER | `reg:<hash(normalizedEmail)>` | 10 | 1 hour |
| EMAIL_VERIFICATION_RESEND | `resend:<hash(normalizedEmail)>` | 3 | 15 minutes |
| FORGOT_PASSWORD | `forgot:<hash(normalizedEmail)>` | 3 | 15 minutes |
| RESET_PASSWORD | `reset:<tokenHashPrefix>` | 10 | 15 minutes |

### 5.1 Login Rate Limiting

- Only counts failed login attempts
- Successful login resets the failure counter for that email
- Uses `tokenHasher.hash(normalizedEmail)` as the key — never stores raw email
- Returns 429 `AUTH_RATE_LIMITED` when exceeded
- Does not expose whether account exists

### 5.2 Anti-Enumeration Under Rate Limiting

- **RESEND / FORGOT_PASSWORD**: When rate-limited, the service returns silently (as if the request succeeded). The controller still returns 200/202. This preserves anti-enumeration: an attacker cannot distinguish "rate-limited" from "email not found."
- **LOGIN**: Returns 429 `AUTH_RATE_LIMITED`. This is acceptable because the error is the same whether the account exists or not.

### 5.3 V0.1 Limitations

- In-memory only — single JVM
- Counter resets on application restart
- Not distributed — does not work across multiple instances
- Suitable for V0.1 single-instance deployment only
- Redis or distributed rate limiting deferred to a future milestone

---

## 6. Security Response Headers

| Header | Value | Scope |
|--------|-------|-------|
| `Cache-Control` | `no-store` | Auth endpoints (login, refresh, logout, me, password/*) |
| `Pragma` | `no-cache` | Auth endpoints |
| `Referrer-Policy` | `no-referrer` | All responses (via Spring Security) |
| `X-Content-Type-Options` | `nosniff` | Default Spring Security |
| `X-Frame-Options` | `DENY` | Default Spring Security |

Implemented via:
- `AuthCacheControlFilter` (servlet filter for auth endpoints)
- `SecurityConfiguration.headers()` (Spring Security headers)

---

## 7. Transaction Boundaries

### 7.1 Audit Writing Strategy

- **Strategy:** Best-effort
- **Behavior:** Audit write failures are caught, logged with sanitized details, and do not propagate
- **Rationale:** User must not be unable to login because audit writing failed
- **Exception:** Critical security events (replay detection) are logged but family revocation is guaranteed via `REQUIRES_NEW` transaction

### 7.2 Rate Limit Check Position

- **Register:** After email normalization, before password policy validation
- **Login:** After email normalization, before user lookup
- **Resend:** After email normalization, before user lookup
- **Forgot Password:** After email normalization, before user lookup
- **Reset Password:** After token hashing, before token lookup

---

## 8. Error Codes

| Code | HTTP | Scenario | Exposes Details |
|------|------|----------|----------------|
| `AUTH_RATE_LIMITED` | 429 | Rate limit exceeded | No |
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong email/password | No |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | Email not verified | No |
| `AUTH_ACCOUNT_DISABLED` | 401 | Account disabled | No |

---

## 9. Test Coverage

### 9.1 Unit Tests

| Suite | Tests | Coverage |
|-------|-------|----------|
| AuditEventTest | 21 | Construction, nullability, metadata sanitization, toString |
| AuditEventWriterTest | 10 | User/system events, failure handling, sensitive data |
| InMemoryAuthRateLimiterTest | 22 | Basic, expiration, independence, reset, cleanup, disabled |

### 9.2 Integration Tests (PostgreSQL 17)

| Suite | Tests | Coverage |
|-------|-------|----------|
| AuthAuditEventIT | 7 | Register/login/forgot audit, append-only, metadata safety |
| AuthRateLimitIT | 4 | Login 429, forgot anti-enumeration, audit on rejection |
| AuthSecurityHardeningIT | 9 | Headers, sensitive data, anti-enumeration |

### 9.3 Total

- **513 unit tests** pass, 0 failures
- **28 IT tests** (existing + new) — require Docker for local execution
- CI runs `./mvnw clean verify` with PostgreSQL 17 Testcontainers

---

## 10. File Changes

### New Files

```
audit/domain/model/AuditEvent.java
audit/application/port/out/AuditEventRepository.java
audit/application/service/AuditEventWriter.java
audit/infrastructure/persistence/SpringDataAuditEventRepository.java
audit/infrastructure/persistence/AuditEventRepositoryAdapter.java
identity/application/port/out/AuthAuditWriter.java
identity/application/port/out/AuthRateLimiter.java
identity/application/ratelimit/AuthRateLimitAction.java
identity/application/ratelimit/RateLimitDecision.java
identity/application/exception/AuthRateLimitedException.java
identity/infrastructure/audit/AuthAuditWriterAdapter.java
identity/infrastructure/ratelimit/AuthRateLimitProperties.java
identity/infrastructure/ratelimit/InMemoryAuthRateLimiter.java
identity/infrastructure/security/AuthCacheControlFilter.java
```

### Modified Files

```
identity/application/service/RegisterUserService.java
identity/application/service/LoginService.java
identity/application/service/ResendVerificationEmailService.java
identity/application/service/ConfirmEmailVerificationService.java
identity/application/service/RefreshTokenService.java
identity/application/service/LogoutService.java
identity/application/service/ChangePasswordService.java
identity/application/service/ForgotPasswordService.java
identity/application/service/ResetPasswordService.java
identity/api/IdentityExceptionHandler.java
identity/infrastructure/security/SecurityConfiguration.java
identity/infrastructure/config/IdentityConfiguration.java
application.yml
```

### Database Migrations

```
V001–V007: UNMODIFIED
No new migrations
```

---

## 11. Production Release Status

**Production release is NOT allowed.** Remaining blockers:

1. Real `VerificationEmailSender` implementation (currently noop)
2. Real `PasswordResetEmailSender` implementation (currently noop)
3. Complete OpenAPI documentation
4. Pre-release security testing
5. Production configuration review
6. Distributed rate limiting for multi-instance deployments

---

## 12. Next Steps (MF-BE-012)

Recommended scope for MF-BE-012:

1. Real email provider integration (SMTP or transactional email service)
2. Complete OpenAPI documentation for all auth endpoints
3. Pre-release security review and acceptance testing
4. Production configuration and deployment readiness
