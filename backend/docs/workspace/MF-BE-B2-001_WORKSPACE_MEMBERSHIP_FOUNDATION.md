# MF-BE-B2-001: Workspace & Membership Foundation

**Task ID:** MF-BE-B2-001
**Status:** Complete
**Branch:** `feat/MF-BE-B2-001-workspace-membership-foundation`
**Base:** `main` (HEAD: `b28f053`)

---

## 1. Task Scope

This task implements the Workspace B2 stage foundation for Pilot MVP V0.1:

- Workspace JPA Entity mapped to V003 `workspace` table
- WorkspaceMembership JPA Entity mapped to V003 `workspace_membership` table
- Workspace creation with automatic OWNER membership
- Current user workspace query
- Workspace detail query with access control
- Workspace basic info update (name, timezone, currency)
- Workspace permission checking (OWNER-only for write, member for read)
- Workspace audit events
- OpenAPI documentation
- Unit, integration, security, constraint, and audit tests

**Explicitly NOT implemented:**

- Project, Milestone, Task
- Member invitation / acceptance
- Member list query
- Role changes / transfer Owner
- Member removal
- Workspace archive / restore / delete
- Billing
- Public Link
- Redis / JWT / OAuth2 / MFA / SSO

---

## 2. Workspace Table Mapping

Entity: `com.milestoneflow.workspace.domain.model.Workspace`
Table: `workspace` (V003)

| Column | Java Type | JPA Mapping | Notes |
|--------|-----------|-------------|-------|
| `id` | `UUID` | `@Id` | UUID v7, client-side generated |
| `name` | `String` | `@Column(120)` | Display name |
| `slug` | `String` | `@Column(80, unique)` | URL-friendly, unique |
| `default_currency` | `String` | `@Column(3)` | char(3), e.g. "TWD" |
| `timezone` | `String` | `@Column(64)` | IANA timezone |
| `status` | `WorkspaceStatus` | `@Enumerated(STRING)` | ACTIVE, SUSPENDED, ARCHIVED |
| `settings` | `Map<String, Object>` | `@JdbcTypeCode(JSON)` | jsonb, excluded from toString |
| `archived_at` | `Instant` | nullable | Set on archive |
| `archived_by` | `UUID` | nullable | FK to app_user |
| `version` | `long` | `@Version` | Optimistic lock |
| `created_at` | `Instant` | `@CreatedDate` | Audited |
| `created_by` | `UUID` | `@CreatedBy` | Audited |
| `updated_at` | `Instant` | `@LastModifiedDate` | Audited |
| `updated_by` | `UUID` | `@LastModifiedBy` | Audited |

Extends: `AuditedEntity` → `TimestampedEntity` → `BaseEntity`

---

## 3. Membership Table Mapping

Entity: `com.milestoneflow.workspace.domain.model.WorkspaceMembership`
Table: `workspace_membership` (V003)

| Column | Java Type | JPA Mapping | Notes |
|--------|-----------|-------------|-------|
| `id` | `UUID` | `@Id` | UUID v7 |
| `workspace_id` | `UUID` | `@Column` | FK to workspace (no @ManyToOne) |
| `user_id` | `UUID` | `@Column` | FK to app_user (no @ManyToOne) |
| `role` | `WorkspaceMembershipRole` | `@Enumerated(STRING)` | OWNER only in V0.1 |
| `status` | `WorkspaceMembershipStatus` | `@Enumerated(STRING)` | PENDING, ACTIVE, REMOVED |
| `joined_at` | `Instant` | `@Column` | Set on creation |
| `ended_at` | `Instant` | nullable | Set on removal |
| `version` | `long` | `@Version` | Optimistic lock |
| `created_at` | `Instant` | `@CreatedDate` | Audited |
| `created_by` | `UUID` | `@CreatedBy` | Audited |
| `updated_at` | `Instant` | `@LastModifiedDate` | Audited |
| `updated_by` | `UUID` | `@LastModifiedBy` | Audited |

**Database Constraints:**
- `uk_workspace_membership`: UNIQUE (workspace_id, user_id)
- `uk_workspace_membership_active_owner`: partial unique on (workspace_id) WHERE role='OWNER' AND status='ACTIVE'
- `uk_workspace_membership_active_user`: partial unique on (user_id) WHERE status='ACTIVE' — one active workspace per user in V0.1

---

## 4. API List

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/workspaces` | Cookie + CSRF | Create workspace (becomes OWNER) |
| GET | `/api/v1/workspaces/current` | Cookie | Get current user's workspace |
| GET | `/api/v1/workspaces/{workspaceId}` | Cookie | Get workspace detail (member only) |
| PATCH | `/api/v1/workspaces/{workspaceId}` | Cookie + CSRF | Update workspace (OWNER only) |

---

## 5. Create Workspace Flow

```
CurrentUserPrincipal
→ load AppUser (via identity port)
→ confirm ACTIVE + email verified
→ normalize slug
→ check slug uniqueness
→ check user has no existing active workspace
→ create Workspace (ACTIVE)
→ create WorkspaceMembership (OWNER, ACTIVE)
→ save both in single transaction
→ write audit WORKSPACE_CREATED
→ return WorkspaceResult
```

**Defaults:**
- timezone: `Asia/Taipei`
- defaultCurrency: `TWD`

---

## 6. Current Workspace Query Flow

```
CurrentUserPrincipal
→ findActiveByUserId
→ if none: return 404
→ find Workspace by membership.workspaceId
→ return WorkspaceResult with role
```

**Does NOT auto-create workspace.**

---

## 7. Workspace Permission Model

| Operation | ACTIVE Member | Non-Member | Anonymous |
|-----------|--------------|------------|-----------|
| Create workspace | ✅ (if no existing) | N/A | ❌ 401 |
| Get current workspace | ✅ | N/A | ❌ 401 |
| Get workspace detail | ✅ 200 | ❌ 404 | ❌ 401 |
| Update workspace | ✅ OWNER only | ❌ 404 | ❌ 401 |

**Cross-workspace access:** Returns 404 (not 403) to prevent workspace existence leakage.

---

## 8. OWNER Membership Policy

- Workspace creator automatically becomes OWNER
- Only one ACTIVE membership per user in V0.1
- OWNER is the only role in V0.1
- V0.1 does not support role changes or transfers

---

## 9. Data Isolation Rules

- All workspace-scoped resources must verify `workspace_membership` with `status = ACTIVE`
- Cross-workspace access returns 404 (same as not-found)
- No workspace data is accessible without authentication
- The `WorkspaceAccessChecker` service enforces isolation at the application layer
- Database partial unique indexes provide defense-in-depth

---

## 10. Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `WORKSPACE_NOT_FOUND` | 404 | Workspace not found or access denied |
| `WORKSPACE_SLUG_ALREADY_EXISTS` | 409 | Slug taken by another workspace |
| `WORKSPACE_ALREADY_EXISTS_FOR_USER` | 409 | User already has an active workspace |
| `WORKSPACE_ACCESS_DENIED` | 404 | Non-member (mapped to 404 externally) |
| `WORKSPACE_OWNER_REQUIRED` | 403 | Non-OWNER attempting write operation |
| `VALIDATION_FAILED` | 422 | Request field validation failure |
| `AUTH_UNAUTHENTICATED` | 401 | No valid access token |

---

## 11. Audit Events

| Event | Trigger | Metadata |
|-------|---------|----------|
| `WORKSPACE_CREATED` | POST /workspaces | None |
| `WORKSPACE_UPDATED` | PATCH /workspaces/{id} | `nameChanged`, `timezoneChanged`, `defaultCurrencyChanged` |

All workspace audit events carry non-null `workspaceId`.

---

## 12. OpenAPI

All four workspace endpoints are documented in the OpenAPI specification:

- Security: `cookieAuth` (MF_ACCESS cookie)
- CSRF: `X-XSRF-TOKEN` header for POST/PATCH
- No JWT Bearer scheme
- Schema: `WorkspaceResponse`, `CreateWorkspaceRequest`, `UpdateWorkspaceRequest`

---

## 13. Test Coverage

### Unit Tests
- `WorkspaceTest`: 10 tests (creation, update, toString, equality)
- `WorkspaceMembershipTest`: 6 tests (creation, null rejection, toString, equality)
- `CreateWorkspaceServiceTest`: 14 tests (creation, defaults, audit, slug normalization, validation)
- `WorkspaceAccessCheckerTest`: 6 tests (requireActiveMember, requireOwner, findWorkspaceOrThrow)
- `WorkspaceExceptionHandlerTest`: 5 tests (all exception-to-HTTP mappings)

### Integration Tests (Testcontainers PostgreSQL 17)
- `WorkspaceFlowIT`: Full-stack CRUD (create, query, update, constraint errors, unauthenticated)
- `WorkspaceSecurityIT`: Cross-user access blocked, anonymous blocked
- `WorkspaceConstraintIT`: DB constraints (slug unique, status CHECK, currency CHECK, role CHECK, active user unique)
- `WorkspaceAuditIT`: WORKSPACE_CREATED audit event verification
- `OpenApiWorkspaceDocumentationIT`: Workspace paths in OpenAPI docs

---

## 14. Database Migration Impact

- **V001–V007**: Unmodified
- **New migrations**: None
- All entities validated against existing V003 schema

---

## 15. Next Steps

- **MF-BE-B2-002**: Workspace member query, invitation placeholder, B2 stage closure
- Project B3 stage: Project entity and workspace-scoped project management
