# MF-BE-006: Identity Persistence Model

## 1. Task Scope

MF-BE-006 implements the Java domain models, JPA entity mappings, repository ports,
Spring Data JPA adapters, and persistence integration tests for the Identity module.

Three entities are mapped: `AppUser`, `AuthSession`, and `VerificationToken`.

This task does **not** implement authentication use cases, registration, login,
token generation, Spring Security, or any web/API layer.

## 2. Package Structure

```
com.milestoneflow.identity
├── domain
│   ├── model
│   │   ├── AppUser.java
│   │   ├── AuthSession.java
│   │   └── VerificationToken.java
│   └── type
│       ├── UserStatus.java
│       ├── AuthSessionStatus.java
│       └── VerificationTokenPurpose.java
├── application
│   └── port
│       └── out
│           ├── AppUserRepository.java
│           ├── AuthSessionRepository.java
│           └── VerificationTokenRepository.java
└── infrastructure
    └── persistence
        ├── SpringDataAppUserRepository.java
        ├── SpringDataAuthSessionRepository.java
        ├── SpringDataVerificationTokenRepository.java
        ├── AppUserRepositoryAdapter.java
        ├── AuthSessionRepositoryAdapter.java
        └── VerificationTokenRepositoryAdapter.java

com.milestoneflow.shared.persistence
├── JpaAuditingConfiguration.java
├── BaseEntity.java
├── TimestampedEntity.java
└── AuditedEntity.java
```

Each layer's responsibility:
- **domain.model** — JPA entities with domain behaviour, mapped directly to tables
- **domain.type** — Enum types matching database CHECK constraints
- **application.port.out** — Repository interfaces (no Spring Data dependency)
- **infrastructure.persistence** — Spring Data JPA interfaces and adapters

## 3. Domain Model and JPA Entity Strategy

**Strategy**: Domain model directly serves as JPA entity (Option A per ADR-BE-001).

**Rationale**: ADR-BE-001 (Accepted with changes) permits JPA annotations on domain
models for V0.1 complexity level. This avoids an extra mapping layer between domain
and persistence models while keeping the domain focused.

**Advantages**: Less boilerplate, no mapper classes, direct field access.
**Limitations**: Domain models carry JPA annotations; tests must account for JPA proxying.

## 4. Entity to Table Mapping

### AppUser → `app_user`

| Java Field        | Column               | Type             | Nullable | Notes                     |
|-------------------|----------------------|------------------|----------|---------------------------|
| id                | id                   | UUID             | NO       | PK, UUID v7               |
| email             | email                | String(320)      | NO       |                           |
| emailNormalized   | email_normalized     | String(320)      | NO       | UNIQUE                    |
| displayName       | display_name         | String(100)      | NO       |                           |
| passwordHash      | password_hash        | String(255)      | NO       | Excluded from toString    |
| status            | status               | Enum STRING(32)  | NO       | PENDING_VERIFICATION default |
| locale            | locale               | String(16)       | NO       | zh-TW default             |
| emailVerifiedAt   | email_verified_at    | Instant          | YES      |                           |
| lastLoginAt       | last_login_at        | Instant          | YES      |                           |
| version           | version              | long (@Version)  | NO       | Optimistic lock           |
| createdAt         | created_at           | Instant          | NO       | JPA @CreatedDate          |
| createdBy         | created_by           | UUID             | YES      | JPA @CreatedBy            |
| updatedAt         | updated_at           | Instant          | NO       | JPA @LastModifiedDate     |
| updatedBy         | updated_by           | UUID             | YES      | JPA @LastModifiedBy       |

### AuthSession → `auth_session`

| Java Field        | Column               | Type             | Nullable | Notes                     |
|-------------------|----------------------|------------------|----------|---------------------------|
| id                | id                   | UUID             | NO       | PK                        |
| userId            | user_id              | UUID             | NO       | FK → app_user(id)         |
| accessTokenHash   | access_token_hash    | String(64)       | NO       | UNIQUE, excluded from toString |
| refreshTokenHash  | refresh_token_hash   | String(64)       | NO       | UNIQUE, excluded from toString |
| sessionFamilyId   | session_family_id    | UUID             | NO       |                           |
| refreshGeneration | refresh_generation   | long             | NO       | CHECK ≥ 0                 |
| status            | status               | Enum STRING(24)  | NO       | ACTIVE default            |
| userAgent         | user_agent           | String(512)      | YES      |                           |
| ipAddress         | ip_address           | String(45)       | YES      |                           |
| accessExpiresAt   | access_expires_at    | Instant          | NO       | V007 split                |
| refreshExpiresAt  | refresh_expires_at   | Instant          | NO       | V007 renamed from expires_at |
| lastUsedAt        | last_used_at         | Instant          | YES      |                           |
| revokedAt         | revoked_at           | Instant          | YES      |                           |
| revokeReason      | revoke_reason        | String(48)       | YES      |                           |
| createdAt         | created_at           | Instant          | NO       | JPA @CreatedDate          |

No `version`, `updated_at`, `created_by`, or `updated_by` columns exist on this table.

### VerificationToken → `verification_token`

| Java Field   | Column      | Type             | Nullable | Notes                     |
|--------------|-------------|------------------|----------|---------------------------|
| id           | id          | UUID             | NO       | PK                        |
| userId       | user_id     | UUID             | NO       | FK → app_user(id)         |
| purpose      | purpose     | Enum STRING(48)  | NO       | CHECK constraint           |
| tokenHash    | token_hash  | String(64)       | NO       | UNIQUE, excluded from toString |
| expiresAt    | expires_at  | Instant          | NO       |                           |
| usedAt       | used_at     | Instant          | YES      |                           |
| createdAt    | created_at  | Instant          | NO       | JPA @CreatedDate          |

No `version`, `updated_at`, `revoked_at`, `created_by`, or `updated_by` columns exist on this table.

## 5. Enum Mapping

All enums use `@Enumerated(EnumType.STRING)` with names matching the database CHECK exactly:

- **UserStatus**: `PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`
- **AuthSessionStatus**: `ACTIVE`, `REVOKED`, `EXPIRED`
- **VerificationTokenPurpose**: `EMAIL_VERIFICATION`, `PASSWORD_RESET`

## 6. Auditing Fields

- `@EnableJpaAuditing` configured in `JpaAuditingConfiguration`
- `DateTimeProvider` delegates to the application `Clock` bean (UTC)
- `AuditorAware<UUID>` returns `Optional.empty()` (no security context yet)
- `created_by` / `updated_by` are nullable — no fake system user
- `@CreatedDate` sets `created_at` on first persist (updatable = false)
- `@LastModifiedDate` sets `updated_at` on every persist

## 7. Optimistic Locking

Only `AppUser` has a `version` column with `@Version`. Verified via a two-transaction
integration test that confirms `ObjectOptimisticLockingFailureException` on concurrent
updates.

`AuthSession` and `VerificationToken` do not have version columns.

## 8. Repository Ports

Located in `identity.application.port.out`. Pure Java interfaces with no
Spring Data dependency. Methods:

- **AppUserRepository**: `save`, `findById`, `findByEmailNormalized`, `existsByEmailNormalized`
- **AuthSessionRepository**: `save`, `findById`, `findByAccessTokenHash`, `findByRefreshTokenHash`, `findByUserIdAndStatus`, `findBySessionFamilyId`
- **VerificationTokenRepository**: `save`, `findByTokenHash`, `findByTokenHashAndPurpose`, `findByUserIdAndPurpose`

No delete, pagination, or dynamic query methods are exposed.

## 9. Spring Data Adapters

Each port has:
1. A package-private `SpringData*Repository` extending `JpaRepository` (infrastructure layer only)
2. A `@Component` adapter implementing the port, delegating to the Spring Data repository

Spring Data types do not leak through the port interface.

## 10. Token Hash Security

- `passwordHash` (AppUser), `accessTokenHash` / `refreshTokenHash` (AuthSession), and
  `tokenHash` (VerificationToken) are excluded from `toString()`.
- Raw tokens are never stored; only SHA-256 hashes are persisted.
- No token generation or hash computation is implemented in this milestone.

## 11. UUID Strategy

Per ADR-BE-002: Client-side UUID v7 generation via `IdGenerator`. Entity constructors
receive the pre-generated ID. No database-generated IDs.

## 12. Clock Strategy

Per ADR-BE-005: A single `Clock` bean in UTC (from `TimeConfiguration`) is used.
The JPA `DateTimeProvider` delegates to this clock. Entities never call `Instant.now()`
directly. Domain methods receive `Instant` parameters from the calling service layer.

## 13. Why No `@ManyToOne`

Per ADR-BE-006: Foreign keys are stored as plain `UUID` fields (`userId`), not as
JPA `@ManyToOne` relationships. This prevents:
- Unwanted eager/lazy loading of entity graphs
- Cross-module JPA coupling
- Large object graphs in memory

Application services load related entities explicitly through their own repositories.

## 14. Why Spring Data Is Not Exposed

Per ADR-BE-001: Repository ports are clean Java interfaces. Spring Data `JpaRepository`
lives in the infrastructure package, accessed only through adapter classes. This allows:
- Future replacement of the persistence mechanism
- Testing the application layer without Spring Data
- Clean architectural boundaries enforced by ArchUnit

## 15. Test Coverage

### Unit Tests (domain model)

| Test Class              | Tests | Coverage Area                          |
|-------------------------|-------|----------------------------------------|
| AppUserTest             | 25    | Creation, state transitions, equality  |
| AuthSessionTest         | 28    | Creation, revoke, expiry, toString     |
| VerificationTokenTest   | 18    | Creation, usage, expiry, usability     |

### Integration Tests (PostgreSQL)

| Test Class                    | Area                            |
|-------------------------------|---------------------------------|
| AppUserRepositoryIT           | CRUD, email lookup, constraints |
| AuthSessionRepositoryIT       | CRUD, hash queries, FK, unique  |
| VerificationTokenRepositoryIT | CRUD, hash queries, FK, unique  |
| IdentityOptimisticLockIT      | Concurrent update detection     |
| JpaAuditingIT                 | Timestamp and actor auditing    |

## 16. Known Limitations

1. **No `revoked_at` on `verification_token`**: The database schema does not include
   a `revoked_at` column for this table. Per-token revocation is not supported at the
   persistence level. If needed in future, a V008 migration would be required.

2. **No `version` on `auth_session`**: Optimistic locking is not available for session
   updates. Concurrent refresh scenarios (MF-BE-009) will use pessimistic locking
   (`SELECT FOR UPDATE`) instead.

3. **`AuditorAware` returns empty**: Until Spring Security is integrated, `created_by`
   and `updated_by` remain null. A future task will provide the authenticated actor.

## 17. Input for MF-BE-007

MF-BE-007 (User Registration & Email Verification) can build on:
- `AppUser.create()` factory method
- `AppUser.activateAfterEmailVerification()` domain behaviour
- `VerificationToken.create()` for email verification tokens
- `AppUserRepository` / `VerificationTokenRepository` ports
- JPA auditing infrastructure for timestamps

## 18. Not Implemented in This Milestone

- Registration use case / service
- Login / authentication
- Email verification confirmation flow
- Refresh token rotation
- Password reset flow
- Token generation / hash computation
- Spring Security integration
- Cookie handling
- REST API endpoints
- Workspace or Audit Java models
- Rate limiting
- Any database migration changes
