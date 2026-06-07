# MF-BE-007A: Registration & Email Verification Engineering Closure

## 1. Task Background

MF-BE-007 implemented user registration, email verification, and resend flows.
Engineering review identified four issues that must be resolved before entering
MF-BE-008 (Login & Auth Session):

| # | Issue | Risk |
|---|-------|------|
| 1 | `.claude/` untracked directory in Git | Dirty work tree |
| 2 | Expired token tests use `Thread.sleep` + short TTL | CI flakiness |
| 3 | `DataIntegrityViolationException` mapping too broad | Incorrect error codes for non-email constraints |
| 4 | `NoopVerificationEmailSender` usable in production | Silent registration without email delivery |

---

## 2. Fix Summary

### 2.1 Git Worktree — `.claude/`

**Decision:** Added `/.claude/` to project `.gitignore` (commit `98651bb`).

Rationale: `.claude/` contains only local Claude Code worktree metadata.
It is a developer tool cache, not team-shared configuration.

### 2.2 Deterministic Token Expiry Tests

**Approach:** Replaced `Thread.sleep` with `MutableClock` (commit `f1132dc`).

- `MutableClock` is a test-only utility in `com.milestoneflow.shared.testing`
- Thread-safe via `AtomicReference<Instant>`
- Tests advance the clock programmatically instead of waiting for real time
- No `Thread.sleep`, `TimeUnit.sleep`, or short TTL anywhere in identity tests

**Example test flow:**
```
T0 = 2026-06-07T12:00:00Z  (MutableClock base)
Create token with expiresAt = T0 + 1s
mutableClock.advance(Duration.ofSeconds(2))   → now = T0 + 2s
Confirm → 401 AUTH_VERIFICATION_TOKEN_EXPIRED
```

### 2.3 Precise Data Integrity Exception Mapping

**Approach:** Introduced `ConstraintViolationMapper` (commit `53e16a2`) and
safety-net handler in `IdentityExceptionHandler`.

**Mapping chain:**

1. **Infrastructure layer** (`AppUserRepositoryAdapter`):
   Catches `DataIntegrityViolationException` on `save()`.
   Uses `ConstraintViolationMapper.isDuplicateEmail()` which inspects the
   Hibernate `ConstraintViolationException.getConstraintName()`.
   Only `uk_app_user_email_normalized` → `EmailAlreadyExistsException`.
   Others re-thrown.

2. **API layer** (`IdentityExceptionHandler`):
   - Handles `EmailAlreadyExistsException` → 409 `AUTH_EMAIL_ALREADY_EXISTS`
   - Safety-net `DataIntegrityViolationException` handler uses reflection
     (avoids ArchUnit violation from direct Hibernate dependency in API layer)
     to re-check constraint name.
   - Only `uk_app_user_email_normalized` → 409.
   - All others re-thrown → caught by global handler → 500 `INTERNAL_ERROR`.

**Error mapping table:**

| Exception | HTTP | Code |
|-----------|------|------|
| `uk_app_user_email_normalized` | 409 | `AUTH_EMAIL_ALREADY_EXISTS` |
| Other unique constraint | 500 | `INTERNAL_ERROR` |
| NOT NULL violation | 500 | `INTERNAL_ERROR` |
| CHECK violation | 500 | `INTERNAL_ERROR` |
| FK violation | 500 | `INTERNAL_ERROR` |

**Security guarantees:**
- Response never contains constraint name
- Response never contains SQL
- Response never contains exception class name
- Request ID preserved across all error responses

### 2.4 Production Email Sender Fail-Closed

**Approach:** Dual protection with `@Profile` + `@ConditionalOnProperty`.

```java
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(name = "milestoneflow.email.provider", havingValue = "noop")
public class NoopVerificationEmailSender implements VerificationEmailSender { ... }
```

**Environment behavior:**

| Profile | Property | Noop Created | Startup |
|---------|----------|:------------:|---------|
| local | `milestoneflow.email.provider=noop` | ✓ | Success |
| test | `milestoneflow.email.provider=noop` | ✓ | Success |
| local | *(not set)* | ✗ | Fails |
| prod | *(any)* | ✗ | Fails |

**Fail-closed mechanism:** `EmailVerificationEventListener` requires
`VerificationEmailSender` via constructor injection. In production with no
real sender, no bean satisfies this dependency → `UnsatisfiedDependencyException`
at startup → application cannot start.

**Noop security:**
- Never logs raw token
- Never logs verification URL
- Masks recipient email
- Only logs userId and masked email at INFO level

---

## 3. Test Evidence

### 3.1 New Tests

| Test Class | Tests | Covers |
|------------|:-----:|--------|
| `IdentityExceptionHandlerTest` | 13 | DIVE safety-net, response security, token exceptions, account disabled |
| `VerificationEmailSenderConfigurationTest` | 7 | Bean creation per profile, prod fail-closed, Noop security |
| `ConstraintViolationMapperTest` | 7 | Constraint classification (existing from commit `53e16a2`) |

### 3.2 Test Baseline

| Category | Count |
|----------|:-----:|
| Unit Tests | 262 |
| ArchUnit Rules | 11 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

---

## 4. Release Blocker

**A real `VerificationEmailSender` implementation is required before
production deployment.** The current `NoopVerificationEmailSender` is restricted
to `local` and `test` profiles and cannot be activated in production.

Development of MF-BE-008 and later tasks may proceed.
Production deployment is blocked until MF-BE-012 (email delivery).

---

## 5. Git Handling Decision

`.claude/` is a local developer tool directory containing Claude Code worktree
metadata. Added to project `.gitignore` with `/.claude/` (root-only pattern).
This is a team-level convention — the directory should not be committed by any
developer.

---

## 6. Input for MF-BE-008

MF-BE-008 (Login & Auth Session) can assume:

- All MF-BE-007A issues resolved
- Deterministic test patterns established (`MutableClock`)
- Precise exception mapping in place
- Production fail-closed for email sender
- Clean Git work tree
- 262 unit tests + 11 ArchUnit rules passing
