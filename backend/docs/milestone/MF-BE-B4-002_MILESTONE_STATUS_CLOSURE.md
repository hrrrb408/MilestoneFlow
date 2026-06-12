# MF-BE-B4-002: Milestone Status Workflow and B4 Closure

## Task Scope

Milestone complete/reopen workflow, list status filtering, ordering strategy,
COMPLETED milestone update restrictions, ARCHIVED project constraints,
audit events, OpenAPI documentation, and Milestone B4 phase closure.

Base: `main` HEAD `28d622b` (MF-BE-B4-001 Milestone foundation CRUD).

## 1. Status Model

```
OPEN ──complete()──> COMPLETED
COMPLETED ──reopen()──> OPEN
```

Two states only: `OPEN` and `COMPLETED`. No `ARCHIVED`, `CANCELLED`, or other states.

## 2. Complete Milestone API

```
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/complete
```

### Behaviour

- Requires authentication (MF_ACCESS cookie + CSRF via X-XSRF-TOKEN)
- Requires workspace OWNER role
- Project must belong to path workspaceId
- Project must exist and must NOT be ARCHIVED
- Milestone must belong to path projectId and workspaceId
- Only OPEN milestones can be completed
- After completion: `status = COMPLETED`, `completedAt = now()`, `completedBy = currentUserId`
- Writes `MILESTONE_COMPLETED` audit event

### Error Responses

| Condition | HTTP | Code |
|---|---|---|
| Not authenticated | 401 | AUTH_UNAUTHENTICATED |
| Not OWNER | 403 | WORKSPACE_OWNER_REQUIRED |
| Milestone/project/workspace not found | 404 | MILESTONE_NOT_FOUND / PROJECT_NOT_FOUND |
| Milestone already COMPLETED | 409 | MILESTONE_ALREADY_COMPLETED |
| Project is ARCHIVED | 409 | PROJECT_ARCHIVED |

## 3. Reopen Milestone API

```
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/reopen
```

### Behaviour

- Requires authentication (MF_ACCESS cookie + CSRF via X-XSRF-TOKEN)
- Requires workspace OWNER role
- Project must belong to path workspaceId
- Project must exist and must NOT be ARCHIVED
- Milestone must belong to path projectId and workspaceId
- Only COMPLETED milestones can be reopened
- After reopen: `status = OPEN`, `completedAt = null`, `completedBy = null`
- Writes `MILESTONE_REOPENED` audit event

### Error Responses

| Condition | HTTP | Code |
|---|---|---|
| Not authenticated | 401 | AUTH_UNAUTHENTICATED |
| Not OWNER | 403 | WORKSPACE_OWNER_REQUIRED |
| Milestone/project/workspace not found | 404 | MILESTONE_NOT_FOUND / PROJECT_NOT_FOUND |
| Milestone not COMPLETED | 409 | MILESTONE_NOT_COMPLETED |
| Project is ARCHIVED | 409 | PROJECT_ARCHIVED |

## 4. List Status Filtering

```
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones?status=OPEN
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones?status=COMPLETED
```

### Rules

- No `status` parameter → returns OPEN + COMPLETED
- `status=OPEN` → only OPEN milestones
- `status=COMPLETED` → only COMPLETED milestones
- Invalid status value → 422 MILESTONE_INVALID_STATUS
- Requires ACTIVE workspace membership

## 5. Ordering Strategy

**Default sort:** `due_date ASC NULLS LAST, created_at ASC`

This is implemented via JPQL `ORDER BY m.dueDate ASC NULLS LAST, m.createdAt ASC`
in `SpringDataMilestoneRepository`.

The database index `idx_milestone_project_status_due` on `(project_id, status, due_date ASC NULLS LAST, created_at ASC)`
supports efficient querying with optional status filter.

## 6. COMPLETED Milestone Update Restriction

```
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}
```

- COMPLETED milestones cannot be updated via PATCH
- Returns 409 `MILESTONE_COMPLETED`
- Must reopen first, then update

## 7. ARCHIVED Project Constraints

For archived projects:

| Operation | Allowed |
|---|---|
| List milestones | ✅ (read-only) |
| Get milestone detail | ✅ (read-only) |
| Create milestone | ❌ 409 PROJECT_ARCHIVED |
| Update milestone | ❌ 409 PROJECT_ARCHIVED |
| Complete milestone | ❌ 409 PROJECT_ARCHIVED |
| Reopen milestone | ❌ 409 PROJECT_ARCHIVED |

## 8. Permission Model

| Operation | Authentication | Workspace Role |
|---|---|---|
| List milestones | Required | ACTIVE member |
| Get milestone detail | Required | ACTIVE member |
| Create milestone | Required | ACTIVE member |
| Update milestone | Required | OWNER |
| Complete milestone | Required | OWNER |
| Reopen milestone | Required | OWNER |

Cross-workspace and cross-project access returns 404 (no leakage).

## 9. Data Isolation

- All repository queries include `workspaceId` as first parameter (ADR-BE-006)
- Composite key lookups: `(workspaceId, projectId, milestoneId)`
- Cross-workspace / cross-project → 404
- Non-members → 404 (not 403, to prevent leakage)

## 10. Audit Events

| Event | Action | Target Type | Metadata |
|---|---|---|---|
| Complete milestone | MILESTONE_COMPLETED | MILESTONE | `{ previousStatus: "OPEN", newStatus: "COMPLETED" }` |
| Reopen milestone | MILESTONE_REOPENED | MILESTONE | `{ previousStatus: "COMPLETED", newStatus: "OPEN" }` |

Both events include: `workspace_id`, `actor_id`, `actor_type = USER`, `source = API`.

## 11. Response Changes

`MilestoneResponse` and `MilestoneResult` now include `completedAt`:

```json
{
  "id": "uuid",
  "workspaceId": "uuid",
  "projectId": "uuid",
  "title": "Backend MVP",
  "description": "...",
  "status": "COMPLETED",
  "dueDate": "2026-07-01",
  "completedAt": "2026-06-12T00:00:00Z",
  "createdAt": "...",
  "updatedAt": "..."
}
```

`completedAt` is `null` for OPEN milestones.

## 12. Error Codes Summary

| Code | HTTP | Description |
|---|---|---|
| MILESTONE_ALREADY_COMPLETED | 409 | Milestone is already COMPLETED |
| MILESTONE_NOT_COMPLETED | 409 | Milestone is not COMPLETED (cannot reopen) |
| MILESTONE_COMPLETED | 409 | Cannot update a COMPLETED milestone |
| MILESTONE_INVALID_STATUS | 422 | Invalid status filter value |
| MILESTONE_NOT_FOUND | 404 | Milestone not found or access denied |
| PROJECT_ARCHIVED | 409 | Project is archived |
| PROJECT_NOT_FOUND | 404 | Project not found or access denied |
| WORKSPACE_OWNER_REQUIRED | 403 | OWNER role required |

## 13. Test Coverage

### Integration Tests

| Test Class | Coverage |
|---|---|
| MilestoneStatusFlowIT | Complete/reopen lifecycle, DB state, update restrictions, list filtering, cross-scope isolation |
| MilestoneStatusSecurityIT | OWNER-only enforcement, MEMBER blocked, outsider 404, anonymous 401 |
| MilestoneStatusAuditIT | MILESTONE_COMPLETED and MILESTONE_REOPENED audit events |
| OpenApiMilestoneDocumentationIT | Complete/reopen endpoints, security schemes, no JWT |

### Existing Tests (regression)

| Test Class | Coverage |
|---|---|
| MilestoneFlowIT | CRUD flows (must still pass) |
| MilestoneSecurityIT | Cross-user/project/workspace isolation (must still pass) |
| MilestoneAuditIT | MILESTONE_CREATED/UPDATED events (must still pass) |
| MilestoneConstraintIT | FK, CHECK constraints (must still pass) |

## 14. Database Changes

**None.** V001–V009 unchanged. No new migration.

All required columns (`status`, `completed_at`, `completed_by`) already exist in V009.

## 15. Explicitly NOT Implemented

- Task CRUD
- Task count / progress calculation
- Milestone archive/restore
- Milestone delete
- Milestone drag-sort / position field
- Activity timeline API
- Comments
- Attachments
- Notifications
- Search
- Bulk operations
- Redis
- JWT / OAuth2
- Database migration

## 16. Milestone B4 Phase Closure

Milestone B4 minimum completion criteria:

| Criterion | Status |
|---|---|
| Milestone schema (V009) | ✅ |
| Milestone create/list/detail/update | ✅ |
| Milestone complete/reopen | ✅ |
| Milestone list status filter | ✅ |
| Milestone ordering strategy | ✅ |
| COMPLETED milestone update restriction | ✅ |
| Project-scoped data isolation | ✅ |
| Workspace-scoped data isolation | ✅ |
| Milestone audit (CREATED/UPDATED/COMPLETED/REOPENED) | ✅ |
| Milestone OpenAPI documentation | ✅ |
| Milestone tests | ✅ |
| CI clean verify | Pending CI |

**If CI passes:**

```
Milestone B4 backend foundation completed.
Task B5 can start.
```

## 17. Files Changed

### New Files

```
src/main/java/com/milestoneflow/milestone/application/port/in/CompleteMilestoneUseCase.java
src/main/java/com/milestoneflow/milestone/application/port/in/ReopenMilestoneUseCase.java
src/main/java/com/milestoneflow/milestone/application/service/CompleteMilestoneService.java
src/main/java/com/milestoneflow/milestone/application/service/ReopenMilestoneService.java
src/main/java/com/milestoneflow/milestone/domain/exception/MilestoneAlreadyCompletedException.java
src/main/java/com/milestoneflow/milestone/domain/exception/MilestoneCompletedException.java
src/main/java/com/milestoneflow/milestone/domain/exception/MilestoneInvalidStatusException.java
src/main/java/com/milestoneflow/milestone/domain/exception/MilestoneNotCompletedException.java
src/test/java/com/milestoneflow/milestone/integration/MilestoneStatusFlowIT.java
src/test/java/com/milestoneflow/milestone/integration/MilestoneStatusSecurityIT.java
src/test/java/com/milestoneflow/milestone/integration/MilestoneStatusAuditIT.java
backend/docs/milestone/MF-BE-B4-002_MILESTONE_STATUS_CLOSURE.md
```

### Modified Files

```
src/main/java/com/milestoneflow/milestone/api/MilestoneController.java
src/main/java/com/milestoneflow/milestone/api/MilestoneExceptionHandler.java
src/main/java/com/milestoneflow/milestone/api/dto/MilestoneResponse.java
src/main/java/com/milestoneflow/milestone/application/result/MilestoneResult.java
src/main/java/com/milestoneflow/milestone/application/service/CreateMilestoneService.java
src/main/java/com/milestoneflow/milestone/application/service/GetMilestoneService.java
src/main/java/com/milestoneflow/milestone/application/service/ListMilestonesService.java
src/main/java/com/milestoneflow/milestone/application/service/UpdateMilestoneService.java
src/main/java/com/milestoneflow/milestone/domain/model/Milestone.java
src/test/java/com/milestoneflow/milestone/integration/OpenApiMilestoneDocumentationIT.java
```
