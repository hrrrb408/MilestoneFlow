# MF-BE-B3-002: Project Status Workflow, Archive/Restore API & B3 Closure

**Task ID:** MF-BE-B3-002
**Status:** Completed
**Branch:** `feat/MF-BE-B3-002-project-status-archive-closure`
**Base:** `main` @ `91f0b63`
**Date:** 2026-06-12

---

## 1. Task Scope

This task implements the Project archive/restore workflow, list filtering, ARCHIVED project update restrictions, audit events, OpenAPI documentation, and serves as the B3 closure milestone.

### Completed

- Project `archive()` and `restore()` domain methods
- `POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/archive`
- `POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/restore`
- `GET /api/v1/workspaces/{workspaceId}/projects?includeArchived=true&status=ARCHIVED`
- ARCHIVED project update restriction (409 PROJECT_ARCHIVED)
- Project status audit events (PROJECT_ARCHIVED, PROJECT_RESTORED)
- Project OpenAPI documentation for archive/restore and list filters
- Project B3 stage acceptance documentation

### Explicitly NOT Implemented

- Milestone
- Task
- Progress calculation
- Project delete / hard delete
- Project template / duplication
- Comment / Attachment / Notification
- Redis / JWT / OAuth2 / MFA / SSO
- Database migration (V001–V008 unchanged)

---

## 2. Project Status Model

V008 `project.status` supports:

```text
ACTIVE → ARCHIVED  (via archive)
ARCHIVED → ACTIVE   (via restore)
```

No new statuses were added. V008 already contained `archived_at` and `archived_by` fields.

---

## 3. Archive API

```http
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/archive
```

- Requires authentication (MF_ACCESS cookie)
- Requires workspace OWNER role
- Project must be ACTIVE
- Sets `status = ARCHIVED`, `archivedAt = now()`, `archivedBy = userId`
- Writes audit event `PROJECT_ARCHIVED`
- Returns `ProjectResponse` with `status: "ARCHIVED"` and `archivedAt`

**Error responses:**
- 401 AUTH_UNAUTHENTICATED — not authenticated
- 403 WORKSPACE_OWNER_REQUIRED — not OWNER
- 404 PROJECT_NOT_FOUND — project not found or wrong workspace
- 409 PROJECT_ARCHIVED — already archived

---

## 4. Restore API

```http
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/restore
```

- Requires authentication (MF_ACCESS cookie)
- Requires workspace OWNER role
- Project must be ARCHIVED
- Sets `status = ACTIVE`, clears `archivedAt` and `archivedBy`
- Writes audit event `PROJECT_RESTORED`
- Returns `ProjectResponse` with `status: "ACTIVE"` and `archivedAt: null`

**Error responses:**
- 401 AUTH_UNAUTHENTICATED — not authenticated
- 403 WORKSPACE_OWNER_REQUIRED — not OWNER
- 404 PROJECT_NOT_FOUND — project not found or wrong workspace
- 409 PROJECT_NOT_ARCHIVED — not archived

---

## 5. List Filtering

```http
GET /api/v1/workspaces/{workspaceId}/projects
GET /api/v1/workspaces/{workspaceId}/projects?includeArchived=true
GET /api/v1/workspaces/{workspaceId}/projects?status=ACTIVE
GET /api/v1/workspaces/{workspaceId}/projects?status=ARCHIVED
```

**Filtering rules:**
- `status` parameter takes priority over `includeArchived`
- No params → returns ACTIVE only
- `includeArchived=true` → returns ACTIVE + ARCHIVED
- `status=ACTIVE` → returns ACTIVE only
- `status=ARCHIVED` → returns ARCHIVED only

---

## 6. ARCHIVED Project Update Restriction

`PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}` rejects updates when project is ARCHIVED:

- Returns 409 `PROJECT_ARCHIVED`
- Must restore first before editing
- `updateBasicInfo()` in `Project` entity enforces this at domain level

---

## 7. Permission Model

| Operation | Auth | Role | Note |
|-----------|------|------|------|
| Archive project | Required | OWNER | 403 if not OWNER |
| Restore project | Required | OWNER | 403 if not OWNER |
| List projects | Required | ACTIVE member | Default excludes ARCHIVED |
| List archived | Required | ACTIVE member | via includeArchived/status |
| Get project detail | Required | ACTIVE member | Works for both ACTIVE and ARCHIVED |
| Update project | Required | OWNER | 409 if ARCHIVED |
| Create project | Required | ACTIVE member | Unchanged from B3-001 |

Cross-workspace access returns 404 to prevent information leakage.

---

## 8. Data Isolation

- All queries include `workspaceId` as composite key
- Cross-workspace archive/restore returns 404
- Non-member access returns 404
- Anonymous access returns 401

---

## 9. Audit Events

| Event | Target Type | Metadata |
|-------|------------|----------|
| PROJECT_ARCHIVED | PROJECT | `{previousStatus, newStatus}` |
| PROJECT_RESTORED | PROJECT | `{previousStatus, newStatus}` |

Metadata does not include description, email, token, cookie, password, or secret.

---

## 10. OpenAPI

All endpoints documented with:
- `summary`, `description`
- Path parameters
- Query parameters (list: `includeArchived`, `status`)
- Success and error responses (401, 403, 404, 409, 422)
- `cookieAuth` security scheme
- CSRF token requirement on mutating endpoints
- No JWT Bearer

---

## 11. Test Coverage

### Unit Tests (34 tests, 0 failures)

- `ProjectTest` — create, archive, restore, updateBasicInfo, isArchived, toString
- `ArchiveProjectServiceTest` — archive success, double archive, not found, non-owner, non-member
- `RestoreProjectServiceTest` — restore success, restore active, not found, non-owner
- `ProjectExceptionHandlerTest` — PROJECT_NOT_ARCHIVED → 409

### Integration Tests (new)

- `ProjectArchiveFlowIT` — archive/restore flow, DB verification, list filtering, update restriction, detail access
- `ProjectArchiveSecurityIT` — anonymous, non-member, cross-workspace, CSRF protection
- `ProjectArchiveAuditIT` — PROJECT_ARCHIVED and PROJECT_RESTORED audit event verification
- `OpenApiProjectDocumentationIT` — archive/restore endpoints, includeArchived/status params, cookieAuth

### Regression

- B1 authentication tests — unchanged
- B2 workspace tests — unchanged
- B3-001 project CRUD tests — unchanged

---

## 12. Database Migration

```
V001–V008: UNCHANGED
No new migration: V008 already contains status, archived_at, archived_by
```

---

## 13. Files Changed

### New Files

```
src/main/java/com/milestoneflow/project/application/port/in/ArchiveProjectUseCase.java
src/main/java/com/milestoneflow/project/application/port/in/RestoreProjectUseCase.java
src/main/java/com/milestoneflow/project/application/service/ArchiveProjectService.java
src/main/java/com/milestoneflow/project/application/service/RestoreProjectService.java
src/main/java/com/milestoneflow/project/domain/exception/ProjectNotArchivedException.java
src/test/java/com/milestoneflow/project/application/service/ArchiveProjectServiceTest.java
src/test/java/com/milestoneflow/project/application/service/RestoreProjectServiceTest.java
src/test/java/com/milestoneflow/project/integration/ProjectArchiveFlowIT.java
src/test/java/com/milestoneflow/project/integration/ProjectArchiveSecurityIT.java
src/test/java/com/milestoneflow/project/integration/ProjectArchiveAuditIT.java
backend/docs/project/MF-BE-B3-002_PROJECT_STATUS_ARCHIVE_CLOSURE.md
```

### Modified Files

```
src/main/java/com/milestoneflow/project/domain/model/Project.java
src/main/java/com/milestoneflow/project/domain/exception/ (new ProjectNotArchivedException)
src/main/java/com/milestoneflow/project/api/ProjectController.java
src/main/java/com/milestoneflow/project/api/ProjectExceptionHandler.java
src/main/java/com/milestoneflow/project/api/dto/ProjectResponse.java
src/main/java/com/milestoneflow/project/application/port/in/ListProjectsUseCase.java
src/main/java/com/milestoneflow/project/application/port/out/ProjectRepository.java
src/main/java/com/milestoneflow/project/application/result/ProjectResult.java
src/main/java/com/milestoneflow/project/application/service/CreateProjectService.java
src/main/java/com/milestoneflow/project/application/service/GetProjectService.java
src/main/java/com/milestoneflow/project/application/service/ListProjectsService.java
src/main/java/com/milestoneflow/project/application/service/UpdateProjectService.java
src/main/java/com/milestoneflow/project/infrastructure/persistence/ProjectRepositoryAdapter.java
src/main/java/com/milestoneflow/project/infrastructure/persistence/SpringDataProjectRepository.java
src/test/java/com/milestoneflow/project/api/ProjectExceptionHandlerTest.java
src/test/java/com/milestoneflow/project/domain/model/ProjectTest.java
src/test/java/com/milestoneflow/project/integration/OpenApiProjectDocumentationIT.java
```

### Deleted Files

None.

---

## 14. B3 Closure Judgment

### B3 Minimum Completion Criteria

| Criterion | Status |
|-----------|--------|
| Project schema (V008) | ✅ Complete |
| Project create/list/detail/update | ✅ Complete (B3-001) |
| Project archive/restore | ✅ Complete (B3-002) |
| Project list includeArchived/status | ✅ Complete (B3-002) |
| Workspace-scoped data isolation | ✅ Complete |
| Project audit | ✅ Complete (PROJECT_CREATED, PROJECT_UPDATED, PROJECT_ARCHIVED, PROJECT_RESTORED) |
| Project OpenAPI | ✅ Complete |
| Project tests | ✅ Complete |
| CI clean verify | ⏳ Pending CI |

**Project B3 backend foundation completed.**
**Milestone B4 can start.**

---

## 15. Next Steps

- Milestone B4: Milestone entity and CRUD
- Task B5: Task entity and CRUD (depends on B4)
- Project B3 is closed — no further Project backend work required for V0.1
