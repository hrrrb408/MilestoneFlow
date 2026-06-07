# B1 Implementation Plan

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-06-07 |
| Based On | B1_AUTHENTICATION_BASELINE.md, ADR Review Report |
| Prerequisites | B0 engineering baseline (complete), ADR review (complete) |

---

## Task Overview

| Task | Description | Depends On | Estimated Commits |
|------|-------------|------------|-------------------|
| MF-BE-004 | Apply accepted ADRs + ArchUnit constraints | B0 complete | 3-4 |
| MF-BE-005 | Authentication database migrations | MF-BE-004 | 2 |
| MF-BE-006 | Identity domain model & persistence | MF-BE-005 | 3-4 |
| MF-BE-007 | Registration & email verification | MF-BE-006 | 3-4 |
| MF-BE-008 | Login & authentication session | MF-BE-006, MF-BE-007 | 3-4 |
| MF-BE-009 | Refresh token rotation & replay detection | MF-BE-008 | 2-3 |
| MF-BE-010 | Logout, password change, password reset | MF-BE-008 | 3-4 |
| MF-BE-011 | Auth audit, rate limiting, security hardening | MF-BE-009, MF-BE-010 | 3-4 |
| MF-BE-012 | Auth OpenAPI & full integration tests | MF-BE-011 | 2-3 |

**Dependency graph**:
```
B0 ──→ 004 ──→ 005 ──→ 006 ──→ 007 ──→ 008 ──→ 009 ──→ 011 ──→ 012
                                  └──────→ 008 ──→ 010 ──→ 011 ──→ 012
```

Tasks 007 and 006 can partially overlap. Tasks 009 and 010 can run in parallel after 008.

---

## MF-BE-004: Apply Accepted ADRs & Architecture Constraints

### Goal
Implement the accepted ADR decisions as code constraints before any business code.

### Input
- ADR Review Report
- ADR Decision Matrix
- B0 engineering baseline

### Scope
- Add `IdGenerator` interface + `UUIDv7IdGenerator` implementation
- Add application `Clock` bean
- Update `ApiResponse` with pagination meta structure
- Update `GlobalExceptionHandler` for 409 Conflict scenarios
- Add `@MappedSuperclass` for audit fields
- Add ArchUnit test suite with minimum boundary rules
- Create module package structure: `identity`, `workspace`, `shared`

### Business Rules
None. This is pure infrastructure.

### Database
None.

### Unit Tests
- IdGenerator produces valid UUID v7
- IdGenerator test injection with fixed UUID
- Clock injection with fixed time
- ArchUnit: API layer doesn't access Repository
- ArchUnit: Domain layer doesn't depend on Web
- ArchUnit: shared doesn't depend on business modules
- ArchUnit: Controller doesn't return Entity

### Integration Tests
None new (existing tests must still pass).

### CI Gate
`./mvnw clean verify` passes (22 unit tests + 1 IT).

### Prohibited
- No business Entity, Repository, Service, or Controller.
- No database migrations.
- No Spring Security configuration.

### Acceptance Criteria
- [ ] `IdGenerator` interface exists with `nextId()` method
- [ ] `UUIDv7IdGenerator` produces valid UUID v7 (or temporary UUID v4 until library chosen)
- [ ] Application `Clock` bean configurable for tests
- [ ] Pagination `ApiResponse` meta includes page, size, totalElements, totalPages, hasNext
- [ ] `GlobalExceptionHandler` handles `DataIntegrityViolationException` → 409
- [ ] `@MappedSuperclass` for audit fields exists (not yet used by entities)
- [ ] ArchUnit suite enforces minimum 6 rules
- [ ] All existing tests pass
- [ ] `./mvnw clean verify` succeeds

### Suggested Commits
1. `feat(backend): add IdGenerator interface, Clock bean, and base audit superclass`
2. `refactor(backend): update ApiResponse with pagination meta and 409 handling`
3. `test(backend): add ArchUnit boundary enforcement tests`

---

## MF-BE-005: Authentication Database Migrations

### Goal
Create Flyway migrations for all B1 database tables.

### Input
- B1_AUTHENTICATION_BASELINE §16 (table definitions)
- Database Model V0.1 docs

### Scope
- V002: `app_user` + `verification_token` + `auth_session`
- V003: `workspace` + `workspace_membership`
- V004: `audit_event`
- V005: Indexes and constraints

### Business Rules
None. Schema only.

### Database
All tables from B1_AUTHENTICATION_BASELINE §16.

### Unit Tests
None (migrations are tested via integration tests).

### Integration Tests
- `DatabaseSchemaIT`: all tables exist after migration
- `DatabaseConstraintIT`: unique constraints, CHECK constraints, FK constraints
- `FlywayMigrationIT`: migration runs on empty database, idempotent re-run

### CI Gate
`./mvnw clean verify` passes. New ITs verify schema.

### Prohibited
- No JPA entities (yet).
- No application code changes.
- No seed data with real users.

### Acceptance Criteria
- [ ] V002–V005 migrations exist and run successfully
- [ ] All tables, constraints, and indexes match B1_AUTHENTICATION_BASELINE
- [ ] `flyway_schema_history` records V002–V005
- [ ] JPA `ddl-auto=validate` passes (no entities yet, so no validation errors)
- [ ] Integration tests verify constraint enforcement

### Suggested Commits
1. `db(backend): add identity and authentication tables (V002)`
2. `db(backend): add workspace, membership, audit_event, and indexes (V003-V005)`

---

## MF-BE-006: Identity Domain Model & Persistence

### Goal
Create JPA entities, repositories, and domain services for User, VerificationToken, and AuthSession.

### Input
- B1_AUTHENTICATION_BASELINE §5 (user state machine), §6 (session model), §16 (tables)
- V002–V005 migrations

### Scope
- `app_user` ↔ `UserEntity` (+ JPA auditing, state machine enum)
- `verification_token` ↔ `VerificationTokenEntity`
- `auth_session` ↔ `AuthSessionEntity`
- Repositories: `UserRepository`, `VerificationTokenRepository`, `AuthSessionRepository`
- Domain services: `UserService`, `TokenService`, `SessionService`
- Value objects: `Email` (normalization), `RawToken` / `TokenHash`
- `PasswordEncoder` integration
- `AuditorAware` implementation

### Business Rules
- Email normalization: trim + NFC + lowercase
- Password: BCrypt via DelegatingPasswordEncoder
- User state machine: PENDING_VERIFICATION → ACTIVE → DISABLED
- Token: 32-byte random, SHA-256 hash stored, single use
- Session: opaque tokens, family-based, generation-based rotation

### API
None (internal domain only).

### Database
Entities map to V002–V005 tables. `ddl-auto=validate` must pass.

### Unit Tests
- Email normalization
- Password policy (min 8, max 128)
- User state machine transitions (valid and invalid)
- Token generation and hashing
- Token expiry check
- Session creation, refresh, revocation
- Error code mapping
- IdGenerator integration
- Clock injection
- AuditorAware

### Integration Tests
- email_normalized unique constraint
- Token hash unique constraint
- FK constraints
- Optimistic lock
- Concurrent refresh (single winner)
- JPA validate passes

### CI Gate
`./mvnw clean verify` passes.

### Prohibited
- No Controller or API endpoint.
- No Spring Security filter chain.
- No email sending.
- No rate limiting.

### Acceptance Criteria
- [ ] All entities map to database tables, JPA validate passes
- [ ] Repositories use only typed queries (no bare `findById`)
- [ ] Email normalization produces correct `email_normalized`
- [ ] Password encoding uses DelegatingPasswordEncoder
- [ ] User state machine rejects invalid transitions
- [ ] Token hashing: only SHA-256 hashes stored, never raw tokens
- [ ] Session family and generation work correctly
- [ ] All unit and integration tests pass

### Suggested Commits
1. `feat(identity): add User entity with email normalization and state machine`
2. `feat(identity): add VerificationToken and AuthSession entities`
3. `feat(identity): add domain services for user, token, and session management`
4. `test(identity): add unit and integration tests for identity domain`

---

## MF-BE-007: Registration & Email Verification

### Goal
Implement user registration and email verification flows.

### Input
- MF-BE-006 (domain model)
- B1_AUTHENTICATION_BASELINE §8 (email verification), §14 (API)

### Scope
- `POST /auth/register` — create user + verification token
- `POST /auth/email-verification/resend` — create new token, send email
- `POST /auth/email-verification/confirm` — verify email, activate account
- Email sending adapter (async, outbox pattern for V0.1: log-only or SMTP stub)
- Error codes: AUTH_EMAIL_ALREADY_EXISTS, AUTH_VERIFICATION_TOKEN_INVALID, AUTH_VERIFICATION_TOKEN_EXPIRED

### Business Rules
- Registration creates user with PENDING_VERIFICATION status.
- Verification token: 24-hour expiry, single use, SHA-256 hash only.
- Resend: 60-second cooldown, multiple valid tokens allowed.
- Confirm does NOT auto-login.
- Forgot/register always returns 200/201 even if edge cases.

### API
| Endpoint | Auth | Success | Errors |
|----------|------|---------|--------|
| POST /auth/register | No | 201 | 409, 422 |
| POST /auth/email-verification/resend | No | 200 | 422 |
| POST /auth/email-verification/confirm | No | 200 | 401 |

### Database
Uses `app_user` and `verification_token` tables.

### Unit Tests
- Registration success flow
- Duplicate email → 409
- Weak password → 422
- Token verification success
- Token expired → 401
- Token already used → 401
- Resend cooldown

### Integration Tests
- Full registration → verify → account active flow
- Duplicate email constraint
- Token hash not stored as raw value

### CI Gate
`./mvnw clean verify`.

### Prohibited
- No login implementation.
- No session management.
- No Spring Security filter chain.

### Acceptance Criteria
- [ ] Registration creates user + verification token in single transaction
- [ ] Email verification activates account
- [ ] Duplicate email returns 409 AUTH_EMAIL_ALREADY_EXISTS
- [ ] Invalid/expired token returns correct error codes
- [ ] Resend has cooldown and doesn't reveal email existence
- [ ] All tests pass

### Suggested Commits
1. `feat(identity): add registration endpoint with validation`
2. `feat(identity): add email verification and resend endpoints`
3. `test(identity): add registration and verification integration tests`

---

## MF-BE-008: Login & Authentication Session

### Goal
Implement login, session creation, and Spring Security filter chain.

### Input
- MF-BE-006 (domain model), MF-BE-007 (registration)
- B1_AUTHENTICATION_BASELINE §2 (cookie strategy), §6 (session model), §18 (security)

### Scope
- `POST /auth/login` — validate credentials, create session, set cookies
- Spring Security `SecurityFilterChain` configuration
- Custom `AuthenticationFilter` (reads MF_ACCESS cookie)
- `AuthenticationProvider` (validates token against DB)
- `AuthenticationEntryPoint` (returns 401 ApiErrorResponse)
- `AccessDeniedHandler` (returns 403 ApiErrorResponse)
- CSRF configuration (`CookieCsrfTokenRepository`)
- CORS configuration
- Cookie settings: MF_ACCESS, MF_REFRESH, XSRF-TOKEN

### Business Rules
- Login validates email + password (identical error for wrong email/password).
- Creates auth_session with token hashes.
- Sets HttpOnly Secure cookies.
- Unverified email → 403 AUTH_EMAIL_NOT_VERIFIED.
- Disabled account → 401 AUTH_ACCOUNT_DISABLED.
- CSRF token generated and set on login.

### API
| Endpoint | Auth | Success | Errors |
|----------|------|---------|--------|
| POST /auth/login | No | 200 + cookies | 401, 403 |

### Security
- Spring Security filter chain: CorsFilter → CsrfFilter → AuthFilter → ...
- No HTTP sessions (stateless).
- SecurityContext populated from cookie-validated token.

### Unit Tests
- Login success → session created, cookies set
- Wrong email → 401 identical to wrong password
- Wrong password → 401 identical to wrong email
- Unverified email → 403
- Disabled account → 401
- Cookie attributes (Secure, HttpOnly, SameSite, Path)

### Integration Tests
- Full login flow with TestRestTemplate
- Cookie validation on subsequent requests
- CSRF token flow
- Expired session → 401

### CI Gate
`./mvnw clean verify`.

### Prohibited
- No refresh token rotation (MF-BE-009).
- No logout (MF-BE-010).
- No password reset (MF-BE-010).

### Acceptance Criteria
- [ ] Login sets MF_ACCESS, MF_REFRESH, XSRF-TOKEN cookies with correct attributes
- [ ] Subsequent requests validate access token cookie
- [ ] 401 identical for wrong email and wrong password
- [ ] 403 for unverified email, 401 for disabled account
- [ ] CSRF token set and validated
- [ ] Security integration tests pass

### Suggested Commits
1. `feat(identity): add Spring Security configuration and auth filter`
2. `feat(identity): add login endpoint with session and cookie management`
3. `test(identity): add login and security integration tests`

---

## MF-BE-009: Refresh Token Rotation & Replay Detection

### Goal
Implement refresh token rotation with replay detection.

### Input
- MF-BE-008 (login & sessions)
- B1_AUTHENTICATION_BASELINE §7 (rotation & replay)

### Scope
- `POST /auth/refresh` — rotate tokens, increment generation, set new cookies
- Replay detection — revoke entire session family on reuse
- Concurrent refresh handling

### Business Rules
- Refresh: new token pair, generation +1, same session_family_id.
- Old refresh token hash invalidated in same transaction.
- Replay: if old hash presented → revoke ALL sessions in family.
- Concurrent: first wins, second triggers replay → family revoked.

### API
| Endpoint | Auth | Success | Errors |
|----------|------|---------|--------|
| POST /auth/refresh | No (cookie) | 200 + cookies | 401 |

### Unit Tests
- Refresh success → generation incremented, new cookies
- Expired refresh → 401
- Revoked session → 401
- Replay detection → family revoked, 401 AUTH_REFRESH_REPLAY_DETECTED

### Integration Tests
- Concurrent refresh with Testcontainers PostgreSQL
- Replay detection with two threads
- Cookie path scoping (only sent to /auth/refresh)

### CI Gate
`./mvnw clean verify`.

### Prohibited
- No logout or password management.
- No rate limiting.

### Acceptance Criteria
- [ ] Refresh produces new token pair in single transaction
- [ ] Old refresh token hash no longer valid after refresh
- [ ] Replay of old token revokes entire family
- [ ] Concurrent refresh safely handled
- [ ] All tests pass

### Suggested Commits
1. `feat(identity): add refresh token rotation with generation tracking`
2. `feat(identity): add replay detection and family revocation`
3. `test(identity): add concurrent refresh and replay integration tests`

---

## MF-BE-010: Logout, Password Change & Password Reset

### Goal
Implement logout, password change, and forgot/reset password flows.

### Input
- MF-BE-008 (login), MF-BE-009 (refresh)
- B1_AUTHENTICATION_BASELINE §9 (password reset), §12 (logout)

### Scope
- `POST /auth/logout` — revoke current session, clear cookies
- `POST /auth/logout-all` — revoke all sessions for user
- `POST /auth/password/forgot` — create reset token, send email
- `POST /auth/password/reset` — validate token, update password, revoke all sessions
- `POST /auth/password/change` — validate old password, update, revoke all sessions

### Business Rules
- Logout: revoke current session, clear all cookies.
- Logout-all: revoke ALL sessions, clear cookies.
- Password forgot: always returns 200 (no enumeration). Rate limited.
- Password reset: single use token, 1-hour expiry, revokes all sessions on success.
- Password change: requires current password, revokes all sessions on success.

### API
| Endpoint | Auth | Success | Errors |
|----------|------|---------|--------|
| POST /auth/logout | Yes | 204 | 401 |
| POST /auth/logout-all | Yes | 204 | 401 |
| POST /auth/password/forgot | No | 200 | — |
| POST /auth/password/reset | No | 200 | 401, 422 |
| POST /auth/password/change | Yes | 200 | 401, 422 |

### Unit Tests
- Logout revokes session and clears cookies
- Logout-all revokes all sessions
- Password forgot always 200
- Password reset success revokes all sessions
- Password reset expired token → 401
- Password change success revokes all sessions
- Password change wrong old password → 401

### Integration Tests
- Full forgot → reset → login flow
- Password change → old sessions invalid
- Logout → subsequent requests → 401

### CI Gate
`./mvnw clean verify`.

### Prohibited
- No rate limiting implementation (MF-BE-011).
- No audit events (MF-BE-011).

### Acceptance Criteria
- [ ] Logout revokes session, clears cookies, returns 204
- [ ] Logout-all revokes all active sessions
- [ ] Password forgot always returns 200 regardless of email existence
- [ ] Password reset works with valid token, revokes all sessions
- [ ] Password change requires current password, revokes all sessions
- [ ] All tests pass

### Suggested Commits
1. `feat(identity): add logout and logout-all endpoints`
2. `feat(identity): add password forgot and reset endpoints`
3. `feat(identity): add password change endpoint`
4. `test(identity): add logout and password management integration tests`

---

## MF-BE-011: Auth Audit, Rate Limiting & Security Hardening

### Goal
Add audit events, rate limiting, and security hardening tests.

### Input
- MF-BE-007 through MF-BE-010 (all auth endpoints)
- B1_AUTHENTICATION_BASELINE §11 (rate limiting), §16.6 (audit_event), §19 (tests)

### Scope
- `audit_event` persistence for auth operations
- In-memory rate limiter implementation
- Rate limiting on all auth endpoints
- Security test suite (password not in response, token not in DB, enumeration prevention, etc.)

### Business Rules
- Audit events: USER_REGISTERED, USER_LOGIN_SUCCESS, USER_LOGIN_FAILED, USER_LOGOUT, SESSION_REFRESHED, SECURITY_REFRESH_REPLAY, SECURITY_RATE_LIMITED, PASSWORD_CHANGED, PASSWORD_RESET
- Rate limits per B1_AUTHENTICATION_BASELINE §11.1.
- Metadata prohibition: no passwords, tokens, cookies, or request bodies.

### API
No new endpoints. Enhances existing endpoints.

### Database
Uses `audit_event` table (V004).

### Unit Tests
- Rate limiter: allows N, blocks N+1, resets after window
- Audit event: correct actor, action, summary, no sensitive data

### Integration Tests
- Full audit trail for registration → verify → login → refresh → logout
- Rate limiting enforcement on each endpoint

### Security Tests
All tests from B1_AUTHENTICATION_BASELINE §19.4.

### CI Gate
`./mvnw clean verify`.

### Prohibited
- No Redis.
- No new endpoints.

### Acceptance Criteria
- [ ] All auth operations produce audit events
- [ ] Audit metadata contains no sensitive data
- [ ] Rate limiting enforced on all specified endpoints
- [ ] Rate limited response: 429 AUTH_RATE_LIMITED with Retry-After
- [ ] All security tests pass (§19.4)
- [ ] All tests pass

### Suggested Commits
1. `feat(identity): add audit event persistence for auth operations`
2. `feat(identity): add in-memory rate limiter for auth endpoints`
3. `test(identity): add security test suite for authentication`

---

## MF-BE-012: Auth OpenAPI & Full Integration Tests

### Goal
Comprehensive end-to-end testing and OpenAPI documentation.

### Input
- MF-BE-007 through MF-BE-011 (all auth features)
- B1_AUTHENTICATION_BASELINE §14 (API), §19 (tests)

### Scope
- End-to-end test suite: full auth lifecycle
- OpenAPI spec for auth endpoints (static file, not auto-generated)
- Edge case tests: concurrent operations, expired everything, malformed inputs
- Verify all acceptance criteria from B1_AUTHENTICATION_BASELINE

### Business Rules
All rules from previous tasks combined.

### API
No new endpoints. Documents existing ones.

### Tests
- Full lifecycle: register → verify → login → refresh → change password → re-login → logout
- Edge cases: expired tokens, revoked sessions, concurrent refresh, enumeration prevention
- Security: all §19.4 tests
- CI gate: `./mvnw clean verify` must pass

### CI Gate
`./mvnw clean verify` with ALL tests passing.

### Prohibited
- No new application logic.
- No new migrations.

### Acceptance Criteria
- [ ] End-to-end auth lifecycle test passes
- [ ] All security tests pass
- [ ] OpenAPI spec covers all auth endpoints with request/response examples
- [ ] No token/password/cookie in any test output or doc example
- [ ] `./mvnw clean verify` passes with 0 failures

### Suggested Commits
1. `test(identity): add end-to-end authentication lifecycle tests`
2. `docs(identity): add OpenAPI specification for authentication endpoints`
