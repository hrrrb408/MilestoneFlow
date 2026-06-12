# MF-BE-B3-001: Project Foundation CRUD

| Field | Value |
|-------|-------|
| Task ID | MF-BE-B3-001 |
| Status | Complete (pending CI `clean verify`) |
| Branch | `feat/MF-BE-B3-001-project-foundation-crud` |
| Base | `main` @ `3daf435` |
| Date | 2026-06-12 |

---

## 1. Task Scope

Implements workspace-scoped project foundation for Pilot MVP V0.1:

- Project database schema (V008)
- Project JPA Entity + `ProjectStatus` enum
- Project Repository (Port / Adapter / Spring Data)
- Create Project Use Case
- List Projects Use Case
- Get Project Detail Use Case
- Update Project Use Case
- Project API Controller + DTOs
- Project Exception Handler
- Project Audit Events
- Project OpenAPI Documentation
- Unit + Integration + Security + Constraint + Audit + OpenAPI Tests

---

## 2. Database Structure

### V008__project.sql

New migration `V008__project.sql` creates the `project` table.

**Why V008**: No existing `project` table in V001–V007. The next sequential migration number is V008.

**Table: `project`**

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | — | PK, UUID v7 client-generated |
| `workspace_id` | `uuid` | NOT NULL | — | FK → `workspace(id)` |
| `name` | `varchar(120)` | NOT NULL | — | Display name |
| `description` | `varchar(2000)` | NULL | — | Optional description |
| `status` | `varchar(32)` | NOT NULL | `'ACTIVE'` | CHECK: `ACTIVE`, `ARCHIVED` |
| `start_date` | `date` | NULL | — | Planned start |
| `target_date` | `date` | NULL | — | Target completion |
| `settings` | `jsonb` | NOT NULL | `'{}'` | Extensible config |
| `archived_at` | `timestamptz` | NULL | — | When archived |
| `archived_by` | `uuid` | NULL | — | Who archived |
| `version` | `bigint` | NOT NULL | `0` | Optimistic lock |
| `created_at` | `timestamptz` | NOT NULL | `now()` | JPA Auditing |
| `created_by` | `uuid` | NULL | — | JPA Auditing |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | JPA Auditing |
| `updated_by` | `uuid` | NULL | — | JPA Auditing |

**Constraints**:
- `pk_project` — Primary key on `id`
- `fk_project_workspace` — FK to `workspace(id)`
- `ck_project_status` — `CHECK (status IN ('ACTIVE', 'ARCHIVED'))`
- `ck_project_settings` — `CHECK (jsonb_typeof(settings) = 'object')`
- `ck_project_version` — `CHECK (version >= 0)`
- `ck_project_date_range` — `CHECK (start_date IS NULL OR target_date IS NULL OR start_date <= target_date)`
- `fk_project_created_by` — FK to `app_user(id)`
- `fk_project_updated_by` — FK to `app_user(id)`
- `fk_project_archived_by` — FK to `app_user(id)`

**Indexes**:
- `idx_project_workspace_status_updated` — `(workspace_id, status, updated_at DESC)` for filtered listing
- `idx_project_workspace_created` — `(workspace_id, created_at DESC)` for default sorting

**Why no Milestone / Task tables**: B3-001 scope is project foundation only. Milestone and Task tables belong to B4+.

**V001–V007 unchanged**: No modifications to existing migrations.

---

## 3. Project Entity Mapping

**Package**: `com.milestoneflow.project.domain.model`

- Extends `AuditedEntity` (inherits `createdAt`, `createdBy`, `updatedAt`, `updatedBy`)
- UUID v7 via `IdGenerator`, client-side pre-generated
- No `@ManyToOne` to Workspace (ADR-BE-006: store IDs only)
- `ProjectStatus` enum: `ACTIVE`, `ARCHIVED`
- `settings` JSONB via `@JdbcTypeCode(SqlTypes.JSON)`
- `@Version` for optimistic locking
- `toString()` excludes `settings` to prevent accidental logging

---

## 4. Project API

### 4.1 Create Project

```http
POST /api/v1/workspaces/{workspaceId}/projects
```

- **Auth**: Cookie (MF_ACCESS) + CSRF (X-XSRF-TOKEN)
- **Permission**: ACTIVE member of workspace
- **Request**: `CreateProjectRequest` (name required, dates optional)
- **Response**: 201 + `ProjectResponse`
- **Audit**: `PROJECT_CREATED`
- **Validation**: name 1–120 chars, date range valid

### 4.2 List Projects

```http
GET /api/v1/workspaces/{workspaceId}/projects
```

- **Auth**: Cookie (MF_ACCESS)
- **Permission**: ACTIVE member of workspace
- **Response**: 200 + `ProjectListResponse` (items array)
- **Scope**: Only active projects in the workspace
- **Order**: `created_at DESC`

### 4.3 Get Project

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}
```

- **Auth**: Cookie (MF_ACCESS)
- **Permission**: ACTIVE member of workspace
- **Response**: 200 + `ProjectResponse`
- **Isolation**: Composite key lookup (workspaceId + projectId)
- **Error**: 404 if not found or belongs to other workspace

### 4.4 Update Project

```http
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}
```

- **Auth**: Cookie (MF_ACCESS) + CSRF (X-XSRF-TOKEN)
- **Permission**: OWNER of workspace
- **Request**: `UpdateProjectRequest` (all fields optional)
- **Response**: 200 + `ProjectResponse`
- **Audit**: `PROJECT_UPDATED` with change metadata
- **Guard**: Archived projects cannot be updated

---

## 5. Workspace-Scoped Ownership

All project APIs are nested under `/workspaces/{workspaceId}/projects`:

- Project `workspace_id` is set from path `workspaceId` on creation
- All queries include `workspaceId` as first parameter (ADR-BE-006)
- Cross-workspace access returns 404 (same as B2 workspace isolation)
- `WorkspaceAccessChecker` enforces membership at the application layer

---

## 6. Permission Model

| Operation | Membership | Role |
|-----------|------------|------|
| Create Project | ACTIVE member | Any |
| List Projects | ACTIVE member | Any |
| Get Project | ACTIVE member | Any |
| Update Project | ACTIVE member | OWNER |

**Rationale for OWNER-only updates**:
- V0.1 only has OWNER role
- Prevents accidental modifications by future non-OWNER members
- Consistent with B2 workspace update policy

---

## 7. Data Isolation Strategy

- **Application layer**: `WorkspaceAccessChecker.requireActiveMember()` / `requireOwner()`
- **Repository layer**: All queries include `workspaceId` parameter
- **Database layer**: `fk_project_workspace` FK constraint
- **Cross-workspace**: Returns 404 `PROJECT_NOT_FOUND` (never 403) to prevent leakage
- **Non-member**: Returns 404 `WORKSPACE_NOT_FOUND` (same response as B2)

---

## 8. Error Codes

| Error Code | HTTP | Trigger |
|------------|------|---------|
| `PROJECT_NOT_FOUND` | 404 | Project doesn't exist or wrong workspace |
| `WORKSPACE_NOT_FOUND` | 404 | Non-member access to workspace |
| `WORKSPACE_OWNER_REQUIRED` | 403 | Non-OWNER attempting update |
| `PROJECT_ARCHIVED` | 409 | Updating archived project |
| `PROJECT_INVALID_DATE_RANGE` | 422 | startDate > targetDate |
| `VALIDATION_FAILED` | 422 | Bean Validation failure |
| `AUTH_UNAUTHENTICATED` | 401 | No valid session |

---

## 9. Audit Events

| Event | Target Type | Target ID | Workspace ID | Metadata |
|-------|-------------|-----------|--------------|----------|
| `PROJECT_CREATED` | `PROJECT` | projectId | workspaceId | `{name: "..."}` |
| `PROJECT_UPDATED` | `PROJECT` | projectId | workspaceId | `{nameChanged: true, ...}` |

**Metadata rules**:
- Only boolean change flags (e.g., `nameChanged`, `descriptionChanged`, `startDateChanged`, `targetDateChanged`)
- Never includes full description text, emails, tokens, or passwords

---

## 10. OpenAPI

All 4 project endpoints documented with:
- `@Tag(name = "Projects")`
- `@SecurityRequirement(name = "cookieAuth")`
- `@Operation` with summary and description
- `@ApiResponses` for 200/201/401/403/404/409/422
- No JWT Bearer scheme

Schemas: `ProjectResponse`, `ProjectListResponse`, `CreateProjectRequest`, `UpdateProjectRequest`

---

## 11. Test Coverage

### Unit Tests
- `ProjectTest` — Entity creation, update, toString
- `CreateProjectServiceTest` — Successful creation, date validation
- `ProjectExceptionHandlerTest` — All exception → status mappings

### Integration Tests
- `ProjectFlowIT` — CRUD lifecycle (create → list → get → update)
- `ProjectSecurityIT` — Cross-user, cross-workspace, anonymous
- `ProjectConstraintIT` — DB constraints (status, FK, date range, settings)
- `ProjectAuditIT` — PROJECT_CREATED, PROJECT_UPDATED events
- `OpenApiProjectDocumentationIT` — Endpoints, cookieAuth, schemas, no JWT

---

## 12. Explicitly NOT Implemented

The following are out of scope for B3-001:

- Milestone
- Task
- Project progress calculation
- Project dashboard
- Project member roles (beyond workspace OWNER)
- Project invitation
- Project delete
- Project archive / restore API (status column exists, no API)
- Project status workflow
- Project template
- Project duplication
- Activity timeline API
- Comment
- Attachment
- Notification
- Search
- Bulk operation
- Redis
- JWT / OAuth2 / MFA / SSO

---

## 13. Next Steps

**MF-BE-B3-002**: Project status workflow, archive/restore API, and B3 stage closure.

**Inputs for B3-002**:
- `project` table with `status = ACTIVE | ARCHIVED`
- No status transition API yet
- Archive columns (`archived_at`, `archived_by`) exist but unused
- B1/B2 regression tests must pass

---

## 14. Module Structure

```
com.milestoneflow.project
├── api
│   ├── ProjectController.java
│   ├── ProjectExceptionHandler.java
│   └── dto
│       ├── CreateProjectRequest.java
│       ├── UpdateProjectRequest.java
│       ├── ProjectResponse.java
│       └── ProjectListResponse.java
├── application
│   ├── command
│   │   ├── CreateProjectCommand.java
│   │   └── UpdateProjectCommand.java
│   ├── port
│   │   ├── in
│   │   │   ├── CreateProjectUseCase.java
│   │   │   ├── ListProjectsUseCase.java
│   │   │   ├── GetProjectUseCase.java
│   │   │   └── UpdateProjectUseCase.java
│   │   └── out
│   │       ├── ProjectRepository.java
│   │       └── ProjectAuditWriter.java
│   ├── result
│   │   └── ProjectResult.java
│   └── service
│       ├── CreateProjectService.java
│       ├── ListProjectsService.java
│       ├── GetProjectService.java
│       └── UpdateProjectService.java
├── domain
│   ├── exception
│   │   ├── ProjectNotFoundException.java
│   │   ├── ProjectArchivedException.java
│   │   └── ProjectInvalidDateRangeException.java
│   ├── model
│   │   └── Project.java
│   └── type
│       └── ProjectStatus.java
└── infrastructure
    ├── audit
    │   └── ProjectAuditWriterAdapter.java
    └── persistence
        ├── ProjectRepositoryAdapter.java
        └── SpringDataProjectRepository.java
```
