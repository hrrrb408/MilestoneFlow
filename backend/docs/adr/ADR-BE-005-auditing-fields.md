# ADR-BE-005: Auditing Fields

| Field | Value |
|-------|-------|
| Status | **Accepted with changes** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |
| Decision Date | 2026-06-07 |
| Decision Makers | System Architect |
| Review Reference | ADR_REVIEW_REPORT.md §ADR-BE-005 |
| Changes Required | (1) Clarify JPA auditing ≠ audit_event — separate concerns. (2) System user: created_by is NULL when no authenticated user (honest, no fake users). (3) Use @MappedSuperclass. (4) Timestamp type: Instant. (5) Application Clock bean. |
| Re-review Required | No |

## Background

All mutable aggregates in MilestoneFlow require audit fields (database model doc §01): `created_at`, `created_by`, `updated_at`, `updated_by`, and `version` (optimistic lock). The architecture mandates these fields for traceability and concurrency control.

## Constraints

1. `id uuid` — primary key.
2. `created_at timestamptz NOT NULL DEFAULT now()` — set once on insert.
3. `created_by uuid` — the user who created the record (nullable for system actions).
4. `updated_at timestamptz NOT NULL DEFAULT now()` — updated on every modification.
5. `updated_by uuid` — the user who last modified the record.
6. `version bigint NOT NULL DEFAULT 1` — Hibernate optimistic lock.
7. All timestamps stored in UTC. Business dates use `LocalDate` with workspace timezone.

## Options

### Option A: JPA `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` / `@LastModifiedDate`

```java
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@LastModifiedDate
@Column(name = "updated_at", nullable = false)
private OffsetDateTime updatedAt;

@Version
private long version;
```

### Option B: Database-level `DEFAULT now()` + trigger for `updated_at`

Let PostgreSQL set timestamps via defaults and triggers, with JPA reading them back.

### Option C: Manual setting in domain code

Each application service sets audit fields explicitly before saving.

## Recommendation

**Option A with a custom `AuditorAware` implementation.**

Rationale: JPA auditing is the standard Spring Data approach. It handles `created_at` (insert-only), `updated_at` (auto-update), and `version` (optimistic lock) transparently. A custom `AuditorAware<UUID>` bean extracts the current user from the security context.

## Advantages

- Standard Spring Data mechanism — well understood, well tested.
- `@Version` provides automatic optimistic locking with `OptimisticLockException`.
- `AuditorAware` integrates with the future security context.
- `@CreatedDate` prevents accidental update of creation timestamp.

## Disadvantages and Risks

- `AuditorAware` requires a security context. During migration or background jobs, the "auditor" may be null (system user).
- `@CreatedDate` / `@LastModifiedDate` require `@EntityListeners` on every entity.
- Flyway-created rows (if any) will have null `created_by` unless handled.

## Impact on Tests

- Verify `created_at` is set on first save.
- Verify `updated_at` changes on subsequent saves.
- Verify `version` increments on update.
- Verify `OptimisticLockException` is thrown on concurrent modification.

## Impact on Database

- Every mutable table includes: `created_at timestamptz NOT NULL DEFAULT now()`, `created_by uuid`, `updated_at timestamptz NOT NULL DEFAULT now()`, `updated_by uuid`, `version bigint NOT NULL DEFAULT 1`.
- Flyway migration templates must include these columns.

## Items for Architecture Window Confirmation

- [ ] Confirm whether immutable published versions (quotes, deliveries) should skip `updated_at` / `updated_by`.
- [ ] Confirm whether system-initiated actions (Worker, Flyway) should use a special `created_by` value.
- [ ] Confirm whether a base `@MappedSuperclass` is acceptable for all entities.
