# MF-BE-008: Login & Authentication Session

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-06-08 |
| Task | MF-BE-008 |
| Status | Complete |
| Depends On | MF-BE-007, MF-BE-007A |
| Blocks | MF-BE-009 (Refresh Token Rotation & Replay Detection) |

---

## 1. Task Scope

MF-BE-008 implements the login and authentication session baseline for MilestoneFlow B1.

### Implemented

| Feature | API | Status |
|---------|-----|--------|
| User Login | `POST /api/v1/auth/login` | ✅ |
| Current User | `GET /api/v1/auth/me` | ✅ |
| Access Cookie Authentication | Custom filter | ✅ |
| Refresh Cookie Creation | Set on login | ✅ |
| XSRF-TOKEN Cookie | Set via Spring Security `CookieCsrfTokenRepository` | ✅ |

### Explicitly NOT Implemented

```
POST /api/v1/auth/refresh          → MF-BE-009
POST /api/v1/auth/logout           → MF-BE-010
POST /api/v1/auth/logout-all       → MF-BE-010
GET  /api/v1/auth/sessions         → MF-BE-010
DELETE /api/v1/auth/sessions/{id}  → MF-BE-010
POST /api/v1/auth/password/forgot  → MF-BE-010
POST /api/v1/auth/password/reset   → MF-BE-010
POST /api/v1/auth/password/change  → MF-BE-010
Refresh Token Rotation             → MF-BE-009
Refresh Token Replay Detection      → MF-BE-009
Redis Rate Limiting                 → Future
JWT / OAuth2 / MFA / SSO           → Future
```

---

## 2. Opaque Token Scheme

Per B1 §1 (Frozen):

- **NOT JWT.** Opaque random strings with SHA-256 hash storage.
- Access Token: 32-byte random, Base64URL encoded, SHA-256 hash stored in DB.
- Refresh Token: 32-byte random, Base64URL encoded, SHA-256 hash stored in DB.
- Raw tokens only appear in `Set-Cookie` headers, never in response body, logs, or audit metadata.
- Database stores only 64-character lowercase hex SHA-256 hashes.

---

## 3. Login Flow

```
POST /api/v1/auth/login
→ Bean Validation (@Valid LoginRequest)
→ EmailNormalizer: trim + NFC + toLowerCase
→ AppUserRepository.findByEmailNormalized(normalized)
→ Not found → InvalidCredentialsException (unified with wrong password)
→ passwordEncoder.matches(rawPassword, passwordHash)
→ Mismatch → InvalidCredentialsException
→ User PENDING_VERIFICATION → EmailNotVerifiedException (403)
→ User DISABLED → AccountDisabledException (401)
→ User ACTIVE → continue
→ SecureTokenGenerator.generate() × 2 (access + refresh)
→ TokenHasher.hash() × 2
→ IdGenerator.nextId() for sessionId and sessionFamilyId
→ AuthSession.create(status=ACTIVE, refreshGeneration=0)
→ AppUser.recordSuccessfulLogin(now)
→ @Transactional: save User + AuthSession
→ Controller: build MF_ACCESS, MF_REFRESH cookies
→ Response body: user data only (no tokens, no hashes)
```

### Transaction Boundary

Same transaction covers:

- Read User
- Validate credentials and status
- Create AuthSession
- Update lastLoginAt
- Save both entities

If AuthSession save fails, lastLoginAt update rolls back.

---

## 4. AuthSession Creation

| Field | Value |
|-------|-------|
| id | IdGenerator.nextId() (UUID v7) |
| userId | From authenticated AppUser |
| accessTokenHash | SHA-256 of raw access token (64-char hex) |
| refreshTokenHash | SHA-256 of raw refresh token (64-char hex) |
| sessionFamilyId | IdGenerator.nextId() (new family per login) |
| refreshGeneration | 0 |
| status | ACTIVE |
| accessExpiresAt | now + PT15M (configurable) |
| refreshExpiresAt | now + P30D (configurable) |
| lastUsedAt | null (not set on creation) |
| revokedAt | null |
| revokeReason | null |

---

## 5. Cookie Strategy

Per B1 §2 (Frozen):

| Cookie | HttpOnly | Secure | SameSite | Path | Max-Age | Purpose |
|--------|----------|--------|----------|------|---------|---------|
| MF_ACCESS | Yes | prod: true, local: false | Lax | /api/v1 | 15 min | Access token |
| MF_REFRESH | Yes | prod: true, local: false | Strict | /api/v1/auth/refresh | 30 days | Refresh token |
| XSRF-TOKEN | No | prod: true, local: false | Lax | /api/v1 | Session | CSRF protection |

- Refresh Cookie Path `/api/v1/auth/refresh` matches the future refresh endpoint.
- XSRF-TOKEN is managed by Spring Security `CookieCsrfTokenRepository`.
- Login, register, verify endpoints are in CSRF ignoring list (public POST endpoints).

---

## 6. Access Cookie Authentication

### Filter: `OpaqueAccessTokenAuthenticationFilter`

Per-request authentication flow:

1. Read `MF_ACCESS` cookie
2. Not present → continue unauthenticated
3. Hash raw token via `TokenHasher`
4. Query `AuthSessionRepository.findByAccessTokenHash(hash)`
5. Not found → unauthenticated
6. Session status ≠ ACTIVE → unauthenticated
7. `now >= accessExpiresAt` → unauthenticated
8. Load `AppUser` by `session.userId`
9. Not found → unauthenticated
10. User status ≠ ACTIVE → unauthenticated
11. Create `CurrentUserPrincipal` → set `SecurityContext`

### Security Guarantees

- Never logs raw tokens or hashes
- Never uses refresh token for authentication
- Never modifies database
- Never writes cookies
- Never creates HTTP sessions

### Failure Handling

- All failures produce identical response: 401 `AUTH_UNAUTHENTICATED`
- No exposure of token status, session state, or user details
- `AuthenticationEntryPointImpl` returns unified JSON error response

---

## 7. GET /me

```
GET /api/v1/auth/me
→ Requires valid MF_ACCESS cookie
→ Resolves CurrentUserPrincipal from SecurityContext
→ GetCurrentUserUseCase.getCurrentUser(userId)
→ Returns CurrentUserResponse

Response:
{
  "data": {
    "userId": "019...",
    "email": "user@example.com",
    "displayName": "Test User",
    "status": "ACTIVE"
  },
  "meta": { "requestId": "..." }
}
```

### Fields NOT Returned

```
passwordHash, accessTokenHash, refreshTokenHash,
sessionId, sessionFamilyId, createdAt, updatedAt,
createdBy, updatedBy, version, emailVerifiedAt, lastLoginAt
```

---

## 8. Error Codes

Per B1 §15 (Frozen):

| Code | HTTP | Scenario |
|------|------|----------|
| AUTH_INVALID_CREDENTIALS | 401 | Wrong email or password (unified response) |
| AUTH_EMAIL_NOT_VERIFIED | 403 | PENDING_VERIFICATION user attempts login |
| AUTH_ACCOUNT_DISABLED | 401 | DISABLED user attempts login |
| AUTH_UNAUTHENTICATED | 401 | No valid access cookie for protected endpoint |
| VALIDATION_FAILED | 422 | Bean validation error |
| INVALID_REQUEST | 400 | Malformed JSON |

### Anti-Enumeration

- Email not found and wrong password return identical `AUTH_INVALID_CREDENTIALS` response.
- Same HTTP status (401), same message, same error code.

---

## 9. Security Guarantees

| Guarantee | Verified |
|-----------|----------|
| Password never in response | ✅ |
| PasswordHash never in response | ✅ |
| Raw tokens never in database | ✅ |
| Raw tokens never in logs | ✅ |
| Token hashes never in API response | ✅ |
| Cookie values never in logs | ✅ |
| LoginRequest.toString redacts password | ✅ |
| LoginCommand.toString redacts password | ✅ |
| LoginResult.toString redacts tokens | ✅ |
| No JWT | ✅ |
| No Authorization header support | ✅ |
| Refresh Cookie cannot authenticate /me | ✅ |
| Unverified accounts cannot login | ✅ |
| Disabled accounts cannot login | ✅ |
| No database migration in this task | ✅ |

---

## 10. Authentication Configuration

```yaml
milestoneflow:
  auth:
    access-token-ttl: PT15M      # 15 minutes
    refresh-token-ttl: P30D       # 30 days
    cookies:
      access-name: MF_ACCESS
      refresh-name: MF_REFRESH
      xsrf-name: XSRF-TOKEN
      access-path: /api/v1
      refresh-path: /api/v1/auth/refresh
      same-site-access: Lax
      same-site-refresh: Strict
      secure: false               # local/test; prod overrides to true
```

### Validation

- `access-token-ttl` must be positive
- `refresh-token-ttl` must be positive
- `refresh-token-ttl >= access-token-ttl`
- Cookie names must be non-empty
- Cookie paths must be non-empty
- Production profile sets `secure: true`

---

## 11. Spring Security Configuration

Per B1 §18 (Frozen):

- `SecurityFilterChain`: stateless, no HTTP sessions
- Custom `OpaqueAccessTokenAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- `CookieCsrfTokenRepository` with `XSRF-TOKEN` cookie
- CSRF ignored for public endpoints (register, verify, login, actuator)
- Form login disabled, HTTP Basic disabled, logout disabled
- Custom `AuthenticationEntryPointImpl` returns 401 JSON
- `PasswordEncoder`: `DelegatingPasswordEncoder` (BCrypt default)

### Filter Chain Order

```
RequestIdFilter (existing)
→ CsrfFilter
→ OpaqueAccessTokenAuthenticationFilter
→ ExceptionTranslationFilter
→ AuthorizationFilter
→ Controller
```

### Public Endpoints

```
POST /auth/register
POST /auth/email-verification/resend
POST /auth/email-verification/confirm
POST /auth/login
GET  /actuator/health
GET  /actuator/info
```

### Protected Endpoints

```
GET /auth/me
```

---

## 12. Module Architecture

### New Files

```
identity/api/AuthLoginController.java
identity/api/AuthMeController.java
identity/api/request/LoginRequest.java
identity/api/response/LoginResponse.java
identity/api/response/CurrentUserResponse.java

identity/application/port/in/LoginUseCase.java
identity/application/port/in/GetCurrentUserUseCase.java
identity/application/command/LoginCommand.java
identity/application/result/LoginResult.java
identity/application/result/CurrentUserResult.java
identity/application/service/LoginService.java
identity/application/service/GetCurrentUserService.java

identity/domain/exception/EmailNotVerifiedException.java
identity/domain/exception/InvalidCredentialsException.java

identity/infrastructure/security/OpaqueAccessTokenAuthenticationFilter.java
identity/infrastructure/security/CurrentUserPrincipal.java
identity/infrastructure/security/SecurityConfiguration.java
identity/infrastructure/security/AuthenticationEntryPointImpl.java
identity/infrastructure/security/AuthCookieWriter.java
identity/infrastructure/config/AuthTokenProperties.java
identity/infrastructure/config/AuthCookieProperties.java
```

### Modified Files

```
identity/api/IdentityExceptionHandler.java          (added exception handlers)
identity/infrastructure/config/IdentityConfiguration.java  (added new config properties)
backend/pom.xml                                       (added spring-boot-starter-security)
application.yml / application-local.yml / application-prod.yml / application-test.yml  (auth config)
```

### Deleted Files

```
identity/infrastructure/config/PasswordEncodingConfiguration.java
(Merged into SecurityConfiguration.passwordEncoder())
```

### Database Migrations

```
V001–V007: UNCHANGED
No new migrations in this task.
```

---

## 13. Test Coverage

### Unit Tests

| Test Class | Tests | Covers |
|------------|-------|--------|
| LoginServiceTest | 22 | Login flow, credentials, status checks, token generation, security |
| GetCurrentUserServiceTest | 4 | Current user fetch, disabled user, not found |
| AuthLoginControllerTest | 11 | WebMvcTest for login + /me endpoints |
| AuthRegistrationControllerTest | 16 | WebMvcTest for registration (updated for Security) |
| AuthCookieWriterTest | 20+ | Cookie attributes, Secure flag, clear cookies |
| OpaqueAccessTokenAuthenticationFilterTest | 12 | Filter auth flow, all failure cases |
| AuthCookiePropertiesTest | 7 | Config defaults, blank values |
| AuthTokenPropertiesTest | 5 | Config defaults, validation |
| ArchitectureRulesTest | 11 | All ArchUnit rules pass |

### Integration Tests (PostgreSQL 17, via CI)

| Test Class | Tests |
|------------|-------|
| LoginFlowIT | Login success, session creation, cookie attributes, /me flow |
| ApplicationStartupIT | Application starts, Flyway V001–V007, Hibernate validate |

---

## 14. Inputs for MF-BE-009

MF-BE-009 will implement:

- `POST /api/v1/auth/refresh`
- Refresh Token rotation (generation increment)
- Refresh Token replay detection (family revocation)
- Pessimistic locking for concurrent refresh

MF-BE-008 prepares:

- `AuthSession` entity with `sessionFamilyId`, `refreshGeneration`
- `AuthSessionRepository.findByRefreshTokenHash()`
- `AuthSessionRepository.findBySessionFamilyId()`
- Refresh Cookie already scoped to `/api/v1/auth/refresh`
- `refreshGeneration = 0` on initial login
