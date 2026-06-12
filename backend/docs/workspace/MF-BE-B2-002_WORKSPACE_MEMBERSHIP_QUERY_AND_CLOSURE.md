# MF-BE-B2-002: Workspace Membership Query & B2 Stage Closure

**Task ID:** MF-BE-B2-002
**Status:** Complete (pending CI `clean verify`)
**Branch:** `feat/MF-BE-B2-002-workspace-membership-query-closure`
**Base:** `main` (HEAD: `a5fbb58`)

---

## 1. Task Scope

This task closes the Workspace B2 stage for Pilot MVP V0.1 by adding
membership **read** capability on top of the B2-001 foundation:

- Workspace member roster query (`GET /workspaces/{id}/members`)
- Current user's membership query (`GET /workspaces/{id}/members/me`)
- Membership read permission model (ACTIVE-member read; data isolation)
- Invitation capability **reserved in documentation only** — not implemented
- OpenAPI documentation for the two new endpoints
- Member-view audit events
- B2 stage closure acceptance report

**Explicitly NOT implemented:**

- Project, Milestone, Task
- Real member invitation / invitation email / invitation token
- Accept / reject invitation
- Member removal
- Role changes / Owner transfer
- Membership status mutation
- Workspace archive / restore / delete
- Billing, Public Link
- Redis / JWT / OAuth2 / MFA / SSO
- Any database migration (V001–V007 unmodified; no V008)

---

## 2. API List

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/workspaces/{workspaceId}/members` | Cookie | List ACTIVE members (ACTIVE member only) |
| GET | `/api/v1/workspaces/{workspaceId}/members/me` | Cookie | Current user's membership (ACTIVE member only) |

GET endpoints do **not** require a CSRF token (read-only), consistent with the
existing `GET /workspaces/{id}` endpoint.

---

## 3. Member Roster Query (`GET /workspaces/{id}/members`)

### Flow

```
CurrentUserPrincipal
→ requireActiveMember(workspaceId, userId)        // 404 if not ACTIVE
→ findActiveMembersByWorkspaceId(workspaceId)     // read-side projection
→ map to WorkspaceMembersResult (joinedAt ASC)
→ audit WORKSPACE_MEMBERS_VIEWED (metadata: memberCount)
→ return result
```

### Response (200)

```json
{
  "data": {
    "workspaceId": "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "members": [
      {
        "userId": "0192a3b4-c5d6-7e8f-9a0b-111111111111",
        "email": "owner@example.com",
        "displayName": "Owner",
        "role": "OWNER",
        "status": "ACTIVE",
        "joinedAt": "2026-06-01T12:00:00Z"
      }
    ]
  },
  "meta": { "requestId": "..." }
}
```

---

## 4. Current Membership Query (`GET /workspaces/{id}/members/me`)

### Flow

```
CurrentUserPrincipal
→ requireActiveMember(workspaceId, userId)        // 404 if not ACTIVE
→ map caller's membership to CurrentWorkspaceMembershipResult
→ audit WORKSPACE_MEMBER_SELF_VIEWED (no metadata)
→ return result
```

### Response (200)

```json
{
  "data": {
    "workspaceId": "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "userId": "0192a3b4-c5d6-7e8f-9a0b-111111111111",
    "role": "OWNER",
    "status": "ACTIVE",
    "joinedAt": "2026-06-01T12:00:00Z"
  },
  "meta": { "requestId": "..." }
}
```

The `members/me` response intentionally carries **no** `email`/`displayName` —
only the caller's own role and status.

---

## 5. Permission Model

| Operation | ACTIVE Member | Non-Member | PENDING | REMOVED | Anonymous |
|-----------|---------------|------------|---------|---------|-----------|
| `GET /members` | ✅ 200 | ❌ 404 | ❌ 404 | ❌ 404 | ❌ 401 |
| `GET /members/me` | ✅ 200 | ❌ 404 | ❌ 404 | ❌ 404 | ❌ 401 |

- **Read = ACTIVE member.** Any ACTIVE member may read the roster and their own
  membership. The implementation does **not** hard-code OWNER-only reads, so
  future roles (V0.2+) inherit read access automatically.
- **Non-members, PENDING, and REMOVED** all receive `404 WORKSPACE_NOT_FOUND`
  — identical to not-found — to prevent workspace existence leakage.
- **Anonymous** requests are rejected by Spring Security with `401
  AUTH_UNAUTHENTICATED` before reaching the controller.
- **Cross-workspace** access returns `404`, never `403`.

This reuses the `WorkspaceAccessChecker.requireActiveMember(...)` introduced in
B2-001; no change to the existing OWNER-write (`requireOwner`) path.

---

## 6. ACTIVE / PENDING / REMOVED Rules

- The roster query returns **only** `status = 'ACTIVE'` rows.
- `PENDING` and `REMOVED` memberships are excluded from the roster **and**
  cannot satisfy the access check for either endpoint.
- Ordering is `joined_at ASC` (applied in the projection SQL).
- V0.1 has exactly one ACTIVE member per workspace (the OWNER); the
  implementation scales to additional members without code change.

---

## 7. Member Response Privacy Rules

**Allowed fields (member roster):** `userId`, `email`, `displayName`, `role`,
`status`, `joinedAt`.

**Never exposed:** `passwordHash`, `emailNormalized`, `lastLoginAt`,
`createdBy`, `updatedBy`, `tokenHash`, `accessTokenHash`, `refreshTokenHash`,
`verificationToken`, `resetToken`, `sessionFamilyId`.

This is enforced **structurally**, not by filtering: the read-side projection
(§8) selects only `email` and `display_name` from `app_user`, and the response
DTO (`WorkspaceMemberResponse`) has no field for sensitive data. The
`members/me` response additionally omits `email`/`displayName`.

Returning member email in the roster is acceptable for V0.1: a workspace roster
must display member identity, and no B1/B2 policy forbids it. Only the raw
`email` is returned — never `emailNormalized` (which is an internal uniqueness key).

---

## 8. Read-Side Query Design (ADR-BE-007, Option B)

To enrich the roster with `email`/`displayName` without exposing the
`AppUser` entity or sensitive fields, this task uses a **read-side projection**
in the workspace module rather than modifying the (frozen) identity module.

### Decision: native-SQL interface projection in `workspace.infrastructure`

Rationale:
- ADR-BE-007 constraint #4 forbids cross-module JPQL joins on entities → native
  SQL is required to join `workspace_membership` + `app_user`.
- The identity module is **frozen (封板)** after B1; this task does not modify it.
- A single native query is efficient (no N+1) and selects only safe columns.
- ARCH-005 is satisfied: no cross-module Java dependency (only a SQL schema
  reference, which is exactly what ADR-BE-007 projections are designed for).

### Components

- `WorkspaceMemberProjection` (package-private interface) — the Spring Data
  interface-based projection.
- `SpringDataWorkspaceMembershipRepository.findActiveMembersByWorkspaceId(...)`
  — native query joining the two tables, `ORDER BY joined_at ASC`.
- `WorkspaceMembershipRepositoryAdapter` — maps the projection to the
  application `WorkspaceMemberResult`, so the application layer never sees the
  projection or the `AppUser` entity.
- `WorkspaceMembershipRepository.findActiveMembersByWorkspaceId(UUID)` — the
  application outbound port method.

### N+1 note

V0.1 has one member per workspace, so N+1 is a non-issue. The projection is a
single query regardless, so it scales without code change.

---

## 9. Invitation Capability Reservation

Real invitations are **not implemented**. The following endpoints are reserved
for a future task and documented here only — **no controller endpoint, table,
or migration is introduced**:

```http
POST /api/v1/workspaces/{workspaceId}/invitations          # create invitation
GET  /api/v1/workspaces/{workspaceId}/invitations          # list invitations
POST /api/v1/workspace-invitations/{token}/accept          # accept invitation
```

### Reservation details

- **Reserved API:** the three endpoints above (documented, not coded).
- **Reserved database:** none. No `workspace_invitation` table is created; that
  belongs to a future migration when invitations are implemented.
- **OpenAPI guard:** `OpenApiWorkspaceDocumentationIT.noInvitationEndpoints`
  asserts the published OpenAPI contains **no** `/invitations` or
  `/workspace-invitations` paths — proving the reservation is documentation-only.
- **Suggested future task:** `MF-BE-B2-003` (or a V0.2 stage) — invitation
  entity, `workspace_invitation` migration, invitation token issuance/accept,
  and role assignment on acceptance. Out of scope for B2-002.

---

## 10. Error Codes

| Code | HTTP | Trigger |
|------|------|---------|
| `WORKSPACE_NOT_FOUND` | 404 | Non-member / PENDING / REMOVED / unknown workspace / cross-workspace |
| `AUTH_UNAUTHENTICATED` | 401 | Anonymous request |

No new error code was introduced. Non-member and unknown-workspace collapse to
the same `WORKSPACE_NOT_FOUND` (B2-001 §10 strategy) to avoid leakage. A
`WORKSPACE_MEMBERSHIP_NOT_FOUND` code was considered and **intentionally not
added** — a distinct code would leak "the workspace exists but you are not a
member."

---

## 11. Audit Events

This task adds two **read** audit events (the B2-001 foundation only audited
state changes). Read-audit is added deliberately because the member roster is a
collaboration surface where "who viewed the roster" is security-relevant.

| Event | Trigger | Metadata |
|-------|---------|----------|
| `WORKSPACE_MEMBERS_VIEWED` | `GET /members` success | `{ "memberCount": N }` |
| `WORKSPACE_MEMBER_SELF_VIEWED` | `GET /members/me` success | none |

- Metadata never contains emails, user IDs, or PII — only `memberCount`.
- Denied access (non-member) does **not** produce an audit event; it returns
  404 before the service body runs. The denial is indistinguishable from
  not-found by design.
- Both events carry a non-null `workspaceId` and `targetType =
  "workspace_membership"`.

### Decision note

The task allowed reducing read-audit to failure-only events. We chose to record
successful member-view events (with sanitized metadata) because roster access is
auditable collaboration activity, and the metadata is strictly non-sensitive.

---

## 12. OpenAPI

Both endpoints are documented via `@Operation` / `@ApiResponses` /
`@SecurityRequirement("cookieAuth")`:

- `GET /workspaces/{workspaceId}/members` — 200, 401, 404
- `GET /workspaces/{workspaceId}/members/me` — 200, 401, 404

- Security scheme: `cookieAuth` (MF_ACCESS cookie) — same as all workspace endpoints.
- **No JWT Bearer scheme.**
- GET endpoints do not declare a CSRF requirement (read-only).
- New schemas: `WorkspaceMemberResponse`, `WorkspaceMembersResponse`,
  `CurrentWorkspaceMembershipResponse`.

`OpenApiWorkspaceDocumentationIT.WorkspaceMemberEndpoints` verifies:
- both paths exist with a `get` operation;
- both declare `cookieAuth` security;
- the three response schemas are present;
- no JWT Bearer appears;
- no invitation paths are present.

---

## 13. Test Coverage

### Unit Tests

| Class | Covers |
|-------|--------|
| `ListWorkspaceMembersServiceTest` | ACTIVE member returns roster; empty roster; preserves ordering; audits `WORKSPACE_MEMBERS_VIEWED` with `memberCount`; non-member propagates `WorkspaceAccessDeniedException` and skips projection + audit |
| `GetCurrentWorkspaceMembershipServiceTest` | returns caller membership; audits `WORKSPACE_MEMBER_SELF_VIEWED` with no metadata; non-member propagates exception and skips audit |
| `ArchitectureRulesTest` (unchanged, still passes) | ARCH-005/006/009/010 boundaries hold with the new controller, projection, and adapter |

### Integration Tests (Testcontainers PostgreSQL 17)

| Class | Covers |
|-------|--------|
| `WorkspaceMemberFlowIT` | roster contains the OWNER with correct fields + `requestId` meta; no sensitive fields leak; `members/me` returns OWNER; `members/me` 404 for unknown workspace; `members/me` omits email |
| `WorkspaceMemberSecurityIT` | anonymous 401 (both endpoints); non-member 404 (both); **PENDING** membership 404 (both); **REMOVED** membership 404 (both); non-existent vs inaccessible workspace return identical `404` code (no leakage) |
| `OpenApiWorkspaceDocumentationIT` (extended) | member paths present; `cookieAuth`; response schemas present; no JWT Bearer; no invitation paths |

### Local execution note

Unit tests run green locally on Java 21 (601 tests, 0 failures, 0 errors,
0 skipped, including ArchUnit). Integration tests could not be executed locally
because Docker Desktop returns HTTP 400 with an empty body to Testcontainers'
`/info` ping (the CLI works; the docker-java client used by Testcontainers does
not). This is a local-environment Docker Desktop/Testcontainers incompatibility,
not a code defect — identical in shape to the "Docker Desktop/Ryuk" scenario
called out in the task brief §17. The ITs follow the exact `AbstractIntegrationTest`
pattern of the existing CI-green `WorkspaceFlowIT`/`WorkspaceSecurityIT`, and
rely on GitHub Actions `clean verify` (proper Docker service container) for the
authoritative run. **No ITs are skipped or disabled.**

---

## 14. File Changes

### New (main)

```
workspace/application/result/WorkspaceMemberResult.java
workspace/application/result/WorkspaceMembersResult.java
workspace/application/result/CurrentWorkspaceMembershipResult.java
workspace/application/port/in/ListWorkspaceMembersUseCase.java
workspace/application/port/in/GetCurrentWorkspaceMembershipUseCase.java
workspace/application/service/ListWorkspaceMembersService.java
workspace/application/service/GetCurrentWorkspaceMembershipService.java
workspace/infrastructure/persistence/WorkspaceMemberProjection.java
workspace/api/dto/WorkspaceMemberResponse.java
workspace/api/dto/WorkspaceMembersResponse.java
workspace/api/dto/CurrentWorkspaceMembershipResponse.java
workspace/api/WorkspaceMemberController.java
```

### New (test)

```
workspace/application/service/ListWorkspaceMembersServiceTest.java
workspace/application/service/GetCurrentWorkspaceMembershipServiceTest.java
workspace/integration/WorkspaceMemberFlowIT.java
workspace/integration/WorkspaceMemberSecurityIT.java
```

### Modified

```
workspace/application/port/out/WorkspaceMembershipRepository.java   (+ findActiveMembersByWorkspaceId)
workspace/infrastructure/persistence/SpringDataWorkspaceMembershipRepository.java  (+ native query)
workspace/infrastructure/persistence/WorkspaceMembershipRepositoryAdapter.java     (+ impl + mapping)
workspace/integration/OpenApiWorkspaceDocumentationIT.java          (+ WorkspaceMemberEndpoints)
```

### Database migration

```
V001–V007 unmodified.
No new migration (no V008).
```

---

## 15. Scope Compliance

```
No Project / Milestone / Task:               ✅
No real invitation / token / accept:         ✅ (documentation reservation only)
No role change / Owner transfer:             ✅
No member removal / status mutation:         ✅
No Redis / JWT / OAuth2 / MFA / SSO:         ✅
No database migration:                        ✅
No real secret in code:                       ✅
Identity module (frozen) unmodified:          ✅
```

---

## 16. B2 Stage Closure

B2 minimum completion checklist:

```
✅ Workspace creation
✅ OWNER membership auto-creation
✅ Current workspace query
✅ Workspace detail query
✅ Workspace basic update
✅ Members list query          (B2-002)
✅ Members/me query            (B2-002)
✅ ACTIVE-membership data isolation
✅ OpenAPI documentation
✅ Audit events
⏳ CI clean verify (Java 21, PostgreSQL 17, Flyway V001–V007, Hibernate validate)
```

All code-level B2 conditions are met. The single remaining item is the CI
`clean verify` run, which executes the full B1 authentication regression,
B2-001 workspace regression, and the new B2-002 member ITs.

---

## 17. Next Steps & B3 Readiness

**Decision:**

```text
Workspace B2 backend foundation completed (pending CI).
Project B3 can start once CI clean verify passes.
```

- **MF-BE-B3-001 (suggested):** Project entity, workspace-scoped project
  management. B2's ACTIVE-membership isolation and `WorkspaceAccessChecker`
  are the foundation Project will build on.
- **Invitations** remain a separate future stage (see §9).

Production release is **not** authorized by this task — it is gated by the
release-readiness checklist and a separate release task, per project policy.

---

## 18. PR

- **Base:** `main`
- **Branch:** `feat/MF-BE-B2-002-workspace-membership-query-closure`
- **Title:** `feat(backend): add workspace member queries and B2 closure`
- **Merge:** squash merge after CI `clean verify` passes.
