# ADR Formal Review Report

| Field | Value |
|-------|-------|
| Review Date | 2026-06-07 |
| Reviewer | System Architect |
| Scope | ADR-BE-001 through ADR-BE-010 |
| Input Documents | 10 Proposed ADRs, Architecture V0.1 (8 docs), Database & Domain Model V0.1 (7 docs), Pilot MVP V0 PRD, User Stories, SRS V1.0 |
| Overall Conclusion | **B1 APPROVED** — All blocking ADRs have been reviewed and accepted. Authentication approach is determined by architecture doc §03 (opaque tokens + HttpOnly cookies). B1 may proceed with the caveats listed below. |
| Outstanding Issues | 2 OPEN QUESTIONs (non-blocking, documented in §7) |

## Review Summary

| ADR | Topic | Decision | Blocks B1 | Changes Required |
|-----|-------|----------|-----------|------------------|
| BE-001 | Package & module structure | **Accepted with changes** | No (after changes) | Add specific module list, shared package rules, ArchUnit minimum rules |
| BE-002 | UUID generation | **Accepted** | No | None. Library choice deferred. |
| BE-003 | Money mapping | **Accepted** | No (not needed in B1) | None. Deferred to project/quotation module. |
| BE-004 | JSONB mapping | **Accepted** | No | None. Usage scope is limited. |
| BE-005 | Auditing fields | **Accepted with changes** | No (after changes) | Clarify JPA auditing ≠ audit_event, system user handling, @MappedSuperclass |
| BE-006 | Composite FK & tenant isolation | **Accepted** | No | None. Critical security ADR — already well-defined. |
| BE-007 | Projection query | **Accepted** | No (not needed in B1) | None. Deferred to list/dashboard features. |
| BE-008 | Flyway & JPA startup | **Accepted** | No | None. Already implemented in B0. |
| BE-009 | API response envelope | **Accepted with changes** | No (after changes) | Add pagination meta structure, 204 rules, error response refinements |
| BE-010 | HTTP validation status | **Accepted** | No | None. Aligned with architecture doc §11. |

## Detailed Review

### ADR-BE-001: Package & Module Structure — Accepted with changes

**Verdict**: The flat-package-per-module + ArchUnit approach is correct and matches architecture doc §02.

**Required changes**:
1. Add the specific module list from architecture doc §02:
   - `identity`, `workspace`, `client`, `project`, `quotation`, `baseline`, `delivery`, `changeorder`, `receivable`, `publicaccess`, `fileasset`, `notification`, `audit`, `actioncenter`, `pilotfeedback`, `scheduler`
   - Rename `shared` to match architecture doc naming (`sharedkernel` or `shared`)
2. Freeze these minimum ArchUnit rules for B1:
   - API layer must not access Repository directly
   - Domain layer must not depend on Web layer
   - No module may access another module's `infrastructure` package
   - `shared` must not depend on any business module
   - Controller must not return JPA Entity
   - Cross-module Repository sharing is prohibited
3. Decide: `domain` package may use JPA annotations (acceptable for V0.1 — enforcing full DDD separation adds complexity without clear Pilot benefit).
4. `shared` may contain: `ApiResponse`, `ApiErrorDetail`, `RequestIdFilter`, `GlobalExceptionHandler`, `IdGenerator`, `ClockProvider`, `AuditorAware`, security utilities.
5. `shared` must not contain: business logic, business validation, business error codes, any class specific to a single module.

**No re-review needed** after changes are applied.

### ADR-BE-002: UUID Generation — Accepted

**Verdict**: UUID v7 client-side generation is accepted.

**Decisions**:
- Database column: `uuid` type (never `varchar(36)`).
- ID generated in aggregate constructor before persistence.
- ID generator defined as interface: `IdGenerator { UUID nextId(); }` — allows test injection.
- UUID v7 sort order is an index locality optimization ONLY, not a business timestamp source.
- Database-generated UUID is prohibited for entity PKs.
- Client-provided entity IDs are prohibited.
- API uses standard UUID string format (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).

**Deferred**: Specific UUID v7 library choice. Must be resolved before B1 coding starts. Acceptable candidates: `java-uuid-generator` (Apache 2.0), or a minimal custom implementation.

**Time rollback handling**: UUID v7 implementations handle clock skew internally (typically accepting a small backward drift). If clock skew exceeds threshold, fall back to random suffix. No special application logic needed.

### ADR-BE-003: Money Mapping — Accepted

**Verdict**: `@Embeddable` Money value object with `BigDecimal` amount and `String` currency.

**Decisions**:
- Value object: Yes, immutable record.
- `@Embeddable`: Yes.
- Precision: `numeric(19,4)` — 4 decimal places.
- Currency: ISO 4217, `char(3)` or `varchar(3)`, stored as uppercase string.
- Prohibit `double`/`float`: Yes, enforced by type system.
- Same-currency check: In the Money value object constructor and operations.
- Equality: `compareTo` on amount (ignores scale differences).
- Construction: `setScale(4, RoundingMode.HALF_UP)` enforced in constructor.
- API output: String `"1250.0000"`, never JSON number.
- Negative amounts: Not allowed in Money value object. Business rules and DB CHECK define per-field sign constraints.
- Rounding mode: `HALF_UP`, configurable per workspace in future versions.
- Not needed in B1 (authentication doesn't use money).

### ADR-BE-004: JSONB Mapping — Accepted

**Verdict**: Hibernate 6 native `@JdbcTypeCode(SqlTypes.JSON)`.

**Allowed use cases**:
- Workspace settings (low-risk adjustable configuration).
- Immutable published snapshot content (quote/delivery/change version body).
- Desensitized audit metadata (no tokens, passwords, or emails).
- Idempotency response snapshots.
- Limited infrastructure payloads (email task metadata).

**Prohibited uses**:
- Core relational fields: `workspace_id`, `project_id`, `status`, `amount`, `currency`, `due_date`, `version_no`, foreign key references, user email, auth status, member role.
- JSONB data must not be queried by business-critical WHERE clauses.

**Java type**: Use strongly-typed DTO records (not raw `Map` or `JsonNode`) for known structures. `Map<String, Object>` only for genuinely dynamic content.

**Input validation**: Application-layer validation via Bean Validation on the DTO before persistence.

**JSONB structure migration**: Add new fields with defaults, never remove fields in existing rows. Use Flyway `R__` migrations for view updates.

### ADR-BE-005: Auditing Fields — Accepted with changes

**Verdict**: JPA `AuditingEntityListener` + custom `AuditorAware<UUID>` + `@Version`.

**Required changes**:
1. **Clarify: JPA auditing ≠ audit_event**. JPA auditing records who/when created/modified. `audit_event` records business actions, actor type, request ID, source, business summary, and desensitized metadata. These are separate concerns.
2. **System user handling**: Do NOT create fake system user records. When no authenticated user exists (pre-auth registration, background jobs), `created_by` is `NULL`. This is acceptable and honest.
3. **`@MappedSuperclass`**: Yes, create a base class for common audit fields. Entities extend it.
4. **Timestamp type**: `Instant` (not `OffsetDateTime` or `LocalDateTime`). Stored as `timestamptz` in PostgreSQL. All times in UTC.
5. **Clock**: Application `Clock` bean for testability. Injected into services, used by JPA auditing via `DateTimeProvider`.
6. **`@Version` type**: `long` (primitive, never null).
7. **Immutable records**: Published quote/delivery/change versions still need `version` for optimistic locking during the draft→publish transition, but `updated_at`/`updated_by` may not change after publication.

**No re-review needed** after changes.

### ADR-BE-006: Composite FK & Tenant Isolation — Accepted

**Verdict**: IDs-only approach with composite FK constraints at database level.

**Decisions**:
- All tenant business tables: `workspace_id uuid NOT NULL`.
- Workspace root table: exempt (it IS the tenant).
- Parent tables: `UNIQUE(workspace_id, id)` as secondary unique constraint. PK remains `id` alone.
- Child tables: `FOREIGN KEY (workspace_id, parent_id) REFERENCES parent(workspace_id, id)`.
- Prohibit bare `findById(id)` on business repositories: use `findByWorkspaceIdAndId(workspaceId, id)`.
- workspaceId source: JWT/session claims (NOT request body or URL parameter for internal APIs).
- Cross-tenant vs not-found: Both return 404 (unified invisibility policy).
- No PostgreSQL RLS in V0.1. Compensation: composite FK + application-layer enforcement + security tests.
- Cross-module `@ManyToOne`: Prohibited. Store only IDs.
- Intra-aggregate controlled associations: Allowed for parent-child within the same aggregate (e.g., Project → milestones), but still use composite FK.
- Delete default: `RESTRICT`. Business data uses archive/void actions, not DELETE.

### ADR-BE-007: Projection Query — Accepted

**Verdict**: JPQL DTO for simple queries, native SQL for complex queries.

- Simple: single-entity list, filtered by `workspace_id`, sorted, paginated.
- Complex: multi-table joins, aggregation, dashboard views.
- Interface Projection: Not recommended. Use record-based constructor DTO.
- Returning Entity from API: Prohibited (Controller must not return JPA Entity).
- Native SQL layer: `@Query(nativeQuery=true)` or `JdbcClient` (Spring Boot 3.5+).
- jOOQ: Deferred to V0.2+.
- All native SQL must have Testcontainers PostgreSQL integration tests.
- Cross-module joins in projection: Allowed for read-only projections, but managed through the projection module's infrastructure layer.
- All queries must explicitly include `workspace_id`.
- Not needed in B1 (no list/dashboard features yet).

### ADR-BE-008: Flyway & JPA Startup — Accepted

**Verdict**: Spring Boot default ordering (DataSource → Flyway → Hibernate validate).

- `spring.jpa.hibernate.ddl-auto: validate` — confirmed, never create/update/create-drop.
- `spring.jpa.open-in-view: false` — confirmed.
- Test environment: also validate (same strictness).
- Production: Flyway auto-migrates on application startup (acceptable for V0.1 single-instance deployment).
- V0.1 CI: empty database migration test (already in ApplicationStartupIT).
- Flyway failure: Application MUST fail to start.
- Schema mismatch: Application MUST fail to start.
- Executed `V*` migrations: IMMUTABLE. Never modify. Add new migrations for changes.
- Repeatable migrations (`R__`): Allowed for views and functions only.
- Hibernate update/create/create-drop: PROHIBITED in all profiles.

### ADR-BE-009: API Response Envelope — Accepted with changes

**Verdict**: Explicit `ResponseEntity<ApiResponse<T>>` from every controller.

**Required changes — add the following rules**:

1. **Success response (single object)**:
```json
{ "data": { ... }, "meta": { "requestId": "..." } }
```

2. **Success response (list)**:
```json
{ "data": [ ... ], "meta": { "requestId": "..." } }
```

3. **Success response (paginated)**:
```json
{
  "data": [ ... ],
  "meta": {
    "requestId": "...",
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true
  }
}
```

4. **201 Created**: Include `Location` header AND response body with envelope.
5. **204 No Content**: No body at all. No envelope. Only for truly empty responses.
6. **Empty list**: `{ "data": [], "meta": { "requestId": "..." } }` (200, not 204).
7. **meta.requestId**: Present in both response header (`X-Request-Id`) and response body.
8. **Error response**: Does NOT use the success envelope. Uses `ApiErrorResponse` directly.
9. **timestamp**: Only in error responses. Not in success responses.
10. **Actuator, file download, OpenAPI**: Excluded from envelope wrapping.
11. **ResponseBodyAdvice auto-wrap**: NOT used. Explicit wrapping only.

**No re-review needed** after changes.

### ADR-BE-010: HTTP Validation Status — Accepted

**Verdict**: 400/422 split, aligned with architecture doc §11.

**Decisions**:
- `400 INVALID_REQUEST`: JSON syntax, type conversion, parameter format, unreadable body.
- `422 VALIDATION_FAILED`: Bean Validation, business field constraints.
- `409 Conflict`: Optimistic lock, idempotency key, unique constraint, state transition conflict.
- `401 Unauthorized`: Not authenticated, expired token, revoked session.
- `403 Forbidden`: Authenticated but no permission.
- `404 Not Found`: Resource doesn't exist or cross-tenant (unified).
- `429 Too Many Requests`: Rate limiting.

**Duplicate email on registration**: Returns `409 AUTH_EMAIL_ALREADY_EXISTS` (unique constraint conflict, not validation error). Note: This creates account enumeration risk — see security mitigation in B1 baseline.

**MethodArgumentNotValidException → 422 VALIDATION_FAILED**.
**ConstraintViolationException → 422 VALIDATION_FAILED**.
**HttpMessageNotReadableException → 400 INVALID_REQUEST**.
**MethodArgumentTypeMismatchException → 400 INVALID_REQUEST**.

**Error code ↔ HTTP status**: One error code maps to exactly one HTTP status. No cross-status reuse.

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| ArchUnit rules not comprehensive enough | Medium | Start with minimum rules, expand per sprint |
| UUID v7 library not yet chosen | Low | Must choose before B1 coding starts |
| No PostgreSQL RLS in V0.1 | High | Composite FK + application enforcement + security tests |
| Registration email enumeration via 409 | Medium | Rate limiting + generic error messaging in production |
| No Redis for rate limiting | Medium | Database-based or in-memory rate limiting for V0.1 |

## Blocking Dependencies

None. All blocking ADRs have been reviewed and accepted (with required changes documented above).

## Open Questions

### OPEN-Q-001: UUID v7 Library Selection

- **Question**: Which specific UUID v7 library to use?
- **Impact**: Affects B1 entity ID generation.
- **Recommended default**: `java-uuid-generator` by Toshiaki Maki (Apache 2.0, well-maintained, supports v7).
- **Decision window**: Must be resolved before MF-BE-006 (Identity domain model).
- **Blocks B1**: No — a simple `IdGenerator` interface decouples the choice.

### OPEN-Q-002: Workspace Creation Timing

- **Question**: Does workspace creation happen during registration or as a separate user action?
- **Impact**: Affects B1 database migration scope and registration flow.
- **Recommended default**: Architecture doc §03 and user stories indicate: **User explicitly creates workspace after email verification** (US-WS-001). Registration does NOT auto-create workspace.
- **Decision window**: Must be confirmed before MF-BE-005 (database migrations).
- **Blocks B1**: Partially — B1 auth (register/login) can proceed, but workspace migration depends on this.

## Overall Conclusion

**B1 APPROVED**. All 10 ADRs have been reviewed. 7 are accepted as-is, 3 require changes (non-blocking). The authentication approach is determined by architecture doc §03: opaque tokens stored as HttpOnly cookies with CSRF protection. No new technology decisions are needed to begin B1 development. The two open questions have recommended defaults and do not block initial work.
