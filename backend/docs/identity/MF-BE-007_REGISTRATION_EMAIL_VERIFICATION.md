# MF-BE-007: User Registration, Email Verification & Resend

## 1. Task Scope

Implements the first group of P0 use cases for B1 User Authentication:

- **User Registration** — `POST /api/v1/auth/register`
- **Email Verification Confirmation** — `POST /api/v1/auth/email-verification/confirm`
- **Verification Email Resend** — `POST /api/v1/auth/email-verification/resend`

This task does NOT implement login, authentication sessions, access/refresh tokens, cookies, CSRF, workspace creation, password reset, rate limiting, or audit events.

---

## 2. API

| API | Method | Request | Success | Response | Key Errors |
|-----|--------|---------|---------|----------|------------|
| Register | `POST /auth/register` | `{email, displayName, password}` | 201 Created | `{data: {id, email, status}, meta: {requestId}}` | 409 AUTH_EMAIL_ALREADY_EXISTS, 422 AUTH_PASSWORD_POLICY_VIOLATION |
| Resend | `POST /auth/email-verification/resend` | `{email}` | 202 Accepted | `{data: "...", meta: {requestId}}` | 422 VALIDATION_FAILED |
| Confirm | `POST /auth/email-verification/confirm` | `{token}` | 200 OK | `{data: {id, email, status}, meta: {requestId}}` | 422 AUTH_VERIFICATION_TOKEN_INVALID_OR_EXPIRED |

---

## 3. Registration Flow

```
Request → Bean Validation → Normalize Email → Validate Password Policy
→ Check email_normalized uniqueness (fast fail) → Encode Password (BCrypt)
→ Create AppUser (PENDING_VERIFICATION) → Generate Secure Token
→ Hash Token (SHA-256) → Create VerificationToken (EMAIL_VERIFICATION)
→ Save User + Token in Transaction → Commit → Publish Event
→ AFTER_COMMIT: Send Verification Email → Return 201
```

### Transaction Boundary

- Single `@Transactional` covers: `AppUser.save` + `VerificationToken.save`
- Email sending occurs AFTER_COMMIT via `@TransactionalEventListener`
- Email failure does NOT roll back the registration
- User can use the resend endpoint to recover

---

## 4. Email Normalization

Implemented in `EmailNormalizationResult.normalize()`:

| Output | Rules |
|--------|-------|
| `displayEmail` | `trim()` + NFC normalize, preserves original case |
| `normalizedEmail` | `trim()` + NFC normalize + `toLowerCase(Locale.ROOT)` |

**Not applied:** Gmail dot removal, plus-alias removal, provider-specific rules.

The `email` column stores the display email; `email_normalized` stores the normalized form with a unique constraint (`uk_app_user_email_normalized`).

---

## 5. Password Policy

Implemented in `PasswordPolicy.validate()`:

- Minimum: 8 Unicode code points
- Maximum: 72 bytes UTF-8 (BCrypt limit)
- No mandatory uppercase, lowercase, digit, or special character
- Password is NOT trimmed (leading/trailing spaces are part of the password)
- No Unicode normalization applied to passwords
- Password does NOT appear in exception messages

---

## 6. PasswordEncoder

Uses `spring-security-crypto` (not the full Spring Security starter):

```java
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

- Default algorithm: BCrypt
- Database stores `{bcrypt}$2a$10$...` format
- Includes algorithm identifier prefix for future migration

---

## 7. Token Generation

Uses `SecureRandom` with 256 bits of entropy (32 bytes):

```
32 random bytes → Base64 URL-safe (no padding) → 43 characters
```

Never uses UUID, timestamp, or user ID as token material.

---

## 8. Token Hash

Uses SHA-256 via JDK `MessageDigest`:

- Output: 64-character lowercase hexadecimal string
- New `MessageDigest` instance per invocation (thread-safe)
- Raw token is never persisted or logged

---

## 9. Token TTL

Configuration property: `milestoneflow.auth.email-verification.token-ttl`

- Default: `PT24H` (24 hours)
- Type: `java.time.Duration`
- Must be positive
- Configured via `EmailVerificationProperties` record

---

## 10. Token Single Use

Ensured by pessimistic write lock (`SELECT ... FOR UPDATE`):

1. Hash the raw token with SHA-256
2. Query `verification_token` by hash + purpose with `PESSIMISTIC_WRITE` lock
3. Validate: exists, unused, not expired, correct purpose
4. Mark token `used_at` and activate user in the same transaction
5. Two concurrent confirmations: only one succeeds, the other sees `used_at != null`

---

## 11. Resend Strategy

For PENDING_VERIFICATION users:

1. Delete all unused `EMAIL_VERIFICATION` tokens for the user
2. Generate a new token and hash
3. Create new `VerificationToken`
4. Save and publish event for AFTER_COMMIT email delivery

Does NOT delete `PASSWORD_RESET` tokens.

---

## 12. Anti-Account Enumeration

The resend endpoint always returns `202 Accepted` with the same message regardless of:

- Email does not exist in the system
- User is ACTIVE
- User is DISABLED
- User is PENDING_VERIFICATION (only this case actually sends)

The public response is identical in all cases.

---

## 13. Error Codes

| Code | HTTP | Scenario |
|------|------|----------|
| `VALIDATION_FAILED` | 422 | Bean Validation failure |
| `AUTH_PASSWORD_POLICY_VIOLATION` | 422 | Password too short or too long |
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | Duplicate email registration |
| `AUTH_VERIFICATION_TOKEN_INVALID_OR_EXPIRED` | 422 | Token not found, expired, or already used |
| `AUTH_ACCOUNT_DISABLED` | 403 | Attempting to verify a disabled account |
| `INVALID_REQUEST` | 400 | Malformed JSON |
| `INTERNAL_ERROR` | 500 | Unhandled server error |

---

## 14. Repository Changes

### VerificationTokenRepository (port)

New methods:

- `findByTokenHashAndPurposeForUpdate(String, VerificationTokenPurpose)` — pessimistic lock query
- `deleteUnusedByUserIdAndPurpose(UUID, VerificationTokenPurpose)` — bulk delete unused tokens by purpose

### SpringDataVerificationTokenRepository

- `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the for-update query
- `@Modifying @Query` for the delete operation

### Exception Conversion (MF-BE-007A)

The infrastructure adapter (`AppUserRepositoryAdapter`) uses `ConstraintViolationMapper` to inspect Hibernate's `ConstraintViolationException.getConstraintName()`. Only `uk_app_user_email_normalized` is mapped to `EmailAlreadyExistsException`. All other data integrity errors are re-thrown.

A safety-net handler in `IdentityExceptionHandler` catches `DataIntegrityViolationException` that escape the adapter (e.g., JPA deferred flush) and performs the same constraint name check via reflection (to avoid ArchUnit violations from direct Hibernate dependency in the API layer).

Non-email data integrity violations (NOT NULL, CHECK, FK, other unique constraints) are re-thrown and handled by the global 500 handler. The response never leaks constraint names, SQL, or exception class names.

---

## 15. Concurrent Safety

| Scenario | Mechanism |
|----------|-----------|
| Concurrent registration with same email | Database unique constraint `uk_app_user_email_normalized` + exists check |
| Concurrent token confirmation | Pessimistic write lock on token row |
| Email send after commit failure | AFTER_COMMIT listener, user can resend |

---

## 16. Mail Port

- **Port:** `VerificationEmailSender.send(recipientEmail, displayName, rawToken, locale)`
- **Adapter:** `NoopVerificationEmailSender` (development only, logs masked email)
- **Timing:** AFTER_COMMIT via `@TransactionalEventListener`
- **Failure behavior:** Log error, do NOT roll back transaction, user can resend
- **Production adapter:** Deferred to MF-BE-012

### MF-BE-007A: Production Fail-Closed

`NoopVerificationEmailSender` is restricted to `local` and `test` profiles via `@Profile({"local", "test"})` and `@ConditionalOnProperty(name = "milestoneflow.email.provider", havingValue = "noop")`. Both conditions must be met.

In production, neither condition is satisfied. No `VerificationEmailSender` bean exists, causing `EmailVerificationEventListener` to fail dependency injection at startup → application cannot start (fail-closed).

**Release Blocker:** A real `VerificationEmailSender` implementation is required before production deployment.

---

## 17. Security Guarantees

- Password never appears in logs, exceptions, toString, or API responses
- Raw verification token never persisted to database
- Raw token never appears in logs or exception messages
- Token hash never appears in API responses
- All secret-containing objects implement redacted `toString()`: `[REDACTED]`
- No cookies, no access/refresh tokens, no auth sessions created
- No workspace created during registration

---

## 18. Package Structure

```
com.milestoneflow.identity
├── api
│   ├── AuthRegistrationController
│   ├── IdentityExceptionHandler
│   ├── request/
│   │   ├── RegisterRequest
│   │   ├── ResendVerificationEmailRequest
│   │   └── ConfirmEmailVerificationRequest
│   └── response/
│       ├── RegistrationResponse
│       └── EmailVerificationResponse
├── application
│   ├── command/
│   │   ├── RegisterUserCommand
│   │   ├── ResendVerificationEmailCommand
│   │   └── ConfirmEmailVerificationCommand
│   ├── event/
│   │   ├── EmailVerificationRequestedEvent
│   │   └── EmailVerificationEventListener
│   ├── port/in/
│   │   ├── RegisterUserUseCase
│   │   ├── ResendVerificationEmailUseCase
│   │   └── ConfirmEmailVerificationUseCase
│   ├── port/out/
│   │   ├── SecureTokenGenerator
│   │   ├── TokenHasher
│   │   └── VerificationEmailSender
│   ├── result/
│   │   ├── RegistrationResult
│   │   └── EmailVerificationResult
│   └── service/
│       ├── SecretToken
│       ├── RegisterUserService
│       ├── ResendVerificationEmailService
│       └── ConfirmEmailVerificationService
├── domain
│   ├── exception/
│   │   ├── EmailAlreadyExistsException
│   │   ├── VerificationTokenInvalidException
│   │   └── AccountDisabledException
│   └── policy/
│       ├── EmailNormalizationResult
│       ├── PasswordPolicy
│       └── PasswordPolicyViolation
└── infrastructure
    ├── config/
    │   ├── PasswordEncodingConfiguration
    │   ├── EmailVerificationProperties
    │   └── IdentityConfiguration
    ├── crypto/
    │   ├── SecureRandomTokenGenerator
    │   └── Sha256TokenHasher
    ├── persistence/
    │   ├── AppUserRepositoryAdapter
    │   ├── ConstraintViolationMapper
    │   └── ... (SpringData repositories)
    └── email/
        └── NoopVerificationEmailSender
```

---

## 19. Test Coverage

### Unit Tests

| Test Class | Covers |
|------------|--------|
| `EmailNormalizationResultTest` | 10+ cases: trim, NFC, lowercase, case preservation, null/blank rejection |
| `PasswordPolicyTest` | 12+ cases: min/max length, no complexity, Unicode, byte limit |
| `PasswordEncodingConfigurationTest` | 6 cases: encode, match, prefix, uniqueness |
| `SecureRandomTokenGeneratorTest` | 7+ cases: length, URL-safe, uniqueness (10K), thread safety |
| `Sha256TokenHasherTest` | 7+ cases: length, determinism, thread safety |
| `RegisterUserServiceTest` | 16+ cases: full flow, normalization, encoding, duplicate, concurrent |
| `ResendVerificationEmailServiceTest` | 10+ cases: anti-enumeration, token invalidation, ACTIVE/DISABLED |
| `ConfirmEmailVerificationServiceTest` | 14+ cases: validation, locking, state transitions, error cases |
| `AuthRegistrationControllerTest` | 16+ cases: @WebMvcTest for all 3 endpoints, validation, error codes |
| `ConstraintViolationMapperTest` | 7 cases: constraint classification (MF-BE-007A) |
| `IdentityExceptionHandlerTest` | 13 cases: DIVE safety-net, response security, token/account exceptions (MF-BE-007A) |
| `VerificationEmailSenderConfigurationTest` | 7 cases: profile bean creation, prod fail-closed, Noop security (MF-BE-007A) |

### Integration Tests (PostgreSQL 17)

| Test Class | Covers |
|------------|--------|
| `UserRegistrationIT` | Full registration flow: email normalization, password encoding, token creation, duplicate email, concurrent registration |
| `EmailVerificationIT` | Token confirmation: activation, used/expired/invalid tokens, disabled user |
| `EmailVerificationResendIT` | Resend flow: token rotation, PASSWORD_RESET preservation, anti-enumeration |
| `EmailVerificationConcurrencyIT` | Concurrent confirmation and concurrent registration with same email |

---

## 20. Not Implemented

The following are explicitly NOT part of this task:

- Login / Authentication (MF-BE-008)
- Access Token / Refresh Token / Session
- Cookie / CSRF
- Workspace creation
- Password reset
- Rate limiting (hook deferred to MF-BE-011)
- Audit event writing (MF-BE-011)
- Production email delivery adapter (**Release Blocker** — see §16)
- Database migration (no V008)

---

## 22. MF-BE-007A Engineering Closure

MF-BE-007A resolved four engineering issues identified during pre-MF-BE-008 review.
See [MF-BE-007A_ENGINEERING_CLOSURE.md](MF-BE-007A_ENGINEERING_CLOSURE.md) for full details.

Key changes:
1. `.claude/` added to `.gitignore` — clean work tree
2. Expired token tests use `MutableClock` — no `Thread.sleep`
3. `ConstraintViolationMapper` + handler safety-net — only `uk_app_user_email_normalized` → 409
4. `NoopVerificationEmailSender` restricted to `local`/`test` — production fail-closed

**Development may proceed to MF-BE-008. Production deployment is blocked until a real email sender is implemented.**

---

## 21. Input for MF-BE-008

MF-BE-008 (Login & Auth Session) can assume:

- Users exist with `PENDING_VERIFICATION` or `ACTIVE` status
- Password hashes are stored with `{bcrypt}` prefix via DelegatingPasswordEncoder
- `AppUserRepository` provides `findByEmailNormalized` for login lookup
- `AuthSession` entity and repository are available but not yet used in business logic
- No `SecurityFilterChain` exists yet
