# MF-BE-B5-002: Task Status Closure

## 1. Task Scope

B5-002 completes the Task B5 stage by adding task completion and reopen workflows,
status/priority filtering verification, ordering strategy confirmation, and
COMPLETED task update restrictions.

**Implemented:**

- Task Complete API (`POST .../tasks/{taskId}/complete`)
- Task Reopen API (`POST .../tasks/{taskId}/reopen`)
- `completedAt` / `completedBy` behaviour
- OPEN → COMPLETED state transition
- COMPLETED → OPEN state transition
- Task list `status` filter (OPEN / COMPLETED)
- Task list `priority` filter regression verification
- Task list ordering strategy confirmation
- COMPLETED task update restriction
- ARCHIVED project constraint for complete/reopen
- COMPLETED milestone constraint for complete/reopen
- Task status audit events (TASK_COMPLETED / TASK_REOPENED)
- Task OpenAPI documentation supplement
- B5 stage acceptance documentation

**Not implemented (out of scope):**

- Progress calculation
- Task delete / archive / restore
- Task reorder / drag-and-drop
- Comment / attachment / notification
- Dashboard / Activity timeline
- Redis / JWT / OAuth2 / MFA / SSO
- Database migration (V001–V010 unchanged)

---

## 2. Task Status Model

Two states only, matching `TaskStatus` enum and `V010__task.sql` CHECK constraint:

```
OPEN ──complete()──→ COMPLETED
COMPLETED ──reopen()──→ OPEN
```

No `IN_PROGRESS`, `CANCELLED`, `BLOCKED`, or `ARCHIVED` states.

---

## 3. Complete API

```
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/complete
```

**Behaviour:**

| Check | Result |
|---|---|
| Auth required | 401 if unauthenticated |
| Workspace OWNER required | 403 WORKSPACE_OWNER_REQUIRED |
| Project exists & belongs to workspace | 404 PROJECT_NOT_FOUND |
| Project not ARCHIVED | 409 PROJECT_ARCHIVED |
| Milestone exists & belongs to project | 404 MILESTONE_NOT_FOUND |
| Milestone not COMPLETED | 409 MILESTONE_COMPLETED |
| Task exists & belongs to milestone | 404 TASK_NOT_FOUND |
| Task is OPEN | 409 TASK_ALREADY_COMPLETED if COMPLETED |

**On success:**

- `status` → `COMPLETED`
- `completedAt` → current timestamp
- `completedBy` → actor user ID
- Audit event `TASK_COMPLETED` written
- Returns `TaskResponse` with status `COMPLETED`

---

## 4. Reopen API

```
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/reopen
```

**Behaviour:**

| Check | Result |
|---|---|
| Auth required | 401 if unauthenticated |
| Workspace OWNER required | 403 WORKSPACE_OWNER_REQUIRED |
| Project exists & belongs to workspace | 404 PROJECT_NOT_FOUND |
| Project not ARCHIVED | 409 PROJECT_ARCHIVED |
| Milestone exists & belongs to project | 404 MILESTONE_NOT_FOUND |
| Milestone not COMPLETED | 409 MILESTONE_COMPLETED |
| Task exists & belongs to milestone | 404 TASK_NOT_FOUND |
| Task is COMPLETED | 409 TASK_NOT_COMPLETED if OPEN |

**On success:**

- `status` → `OPEN`
- `completedAt` → `null`
- `completedBy` → `null`
- Audit event `TASK_REOPENED` written
- Returns `TaskResponse` with status `OPEN`

---

## 5. List Status / Priority Filtering

```
GET .../tasks
GET .../tasks?status=OPEN
GET .../tasks?status=COMPLETED
GET .../tasks?priority=HIGH
GET .../tasks?status=OPEN&priority=HIGH
```

| Filter | Behaviour |
|---|---|
| No params | Returns OPEN + COMPLETED |
| `status=OPEN` | Returns only OPEN tasks |
| `status=COMPLETED` | Returns only COMPLETED tasks |
| `priority=LOW/MEDIUM/HIGH` | Returns matching priority |
| `status` + `priority` | Combined AND filter |
| Invalid `status` | 422 TASK_INVALID_STATUS |
| Invalid `priority` | 422 TASK_INVALID_PRIORITY |

Active membership required (not OWNER-only for reads).

---

## 6. Ordering Strategy

Implemented via JPQL in `SpringDataTaskRepository`:

```
ORDER BY
  CASE WHEN status = OPEN THEN 0 ELSE 1 END,     -- OPEN before COMPLETED
  CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END,    -- NULLS LAST
  dueDate ASC,
  CASE WHEN priority = HIGH THEN 0
       WHEN priority = MEDIUM THEN 1
       ELSE 2 END,                                 -- HIGH before MEDIUM before LOW
  createdAt ASC
```

This is consistent across all query variants (no filter, status-only, priority-only, combined).

---

## 7. COMPLETED Task Update Restriction

COMPLETED tasks cannot be updated via `PATCH .../tasks/{taskId}`.

Returns **409 TASK_COMPLETED**.

Rationale: Prevents accidental modification of completed task data. Reopen first.

---

## 8. ARCHIVED Project Behaviour

| Operation | Allowed | Error |
|---|---|---|
| List tasks | ✅ | — |
| Get task detail | ✅ | — |
| Create task | ❌ | 409 PROJECT_ARCHIVED |
| Update task | ❌ | 409 PROJECT_ARCHIVED |
| Complete task | ❌ | 409 PROJECT_ARCHIVED |
| Reopen task | ❌ | 409 PROJECT_ARCHIVED |

---

## 9. COMPLETED Milestone Behaviour

| Operation | Allowed | Error |
|---|---|---|
| List tasks | ✅ | — |
| Get task detail | ✅ | — |
| Create task | ❌ | 409 MILESTONE_COMPLETED |
| Update task | ❌ | 409 MILESTONE_COMPLETED |
| Complete task | ❌ | 409 MILESTONE_COMPLETED |
| Reopen task | ❌ | 409 MILESTONE_COMPLETED |

---

## 10. Permission Model

| Role | List/Get | Create | Update | Complete | Reopen |
|---|---|---|---|---|---|
| OWNER (ACTIVE) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Non-member | 404 | 404 | 404 | 404 | 404 |
| Anonymous | 401 | 401 | 401 | 401 | 401 |

V0.1 only supports OWNER role. No MEMBER role exists yet.

---

## 11. Data Isolation Strategy

- All queries filter by `workspaceId` + `projectId` + `milestoneId` + `taskId`
- Cross-workspace access → 404 (prevents existence leakage)
- Cross-project access → 404
- Cross-milestone access → 404
- Non-member access → 404 (returns same as not-found)

---

## 12. Audit Events

| Event | Trigger | Metadata |
|---|---|---|
| `TASK_COMPLETED` | Complete task | `{ "previousStatus": "OPEN", "newStatus": "COMPLETED" }` |
| `TASK_REOPENED` | Reopen task | `{ "previousStatus": "COMPLETED", "newStatus": "OPEN" }` |

Audit fields: `workspace_id`, `actor_id`, `actor_type=USER`, `source=API`, `target_type=TASK`, `target_id`.

Metadata does not contain: description, email, token, cookie, password, secret.

---

## 13. OpenAPI

New paths documented:

- `POST .../tasks/{taskId}/complete` — with cookieAuth, CSRF
- `POST .../tasks/{taskId}/reopen` — with cookieAuth, CSRF

Schemas: `TaskResponse`, `TaskListResponse` confirmed.

No JWT Bearer tokens appear.

---

## 14. Test Coverage

### Domain Tests (`TaskTest`)

- `complete()`: sets status/completedAt/completedBy, rejects if already COMPLETED
- `reopen()`: clears status/completedAt/completedBy, rejects if not COMPLETED
- `isCompleted()`: correct for OPEN, COMPLETED, after reopen
- `updateBasicInfo()`: rejects on COMPLETED task, allows after reopen

### Service Tests

- `CompleteTaskServiceTest`: 9 tests — success, not found, already completed, archived, milestone completed, not owner, project not found, milestone not found, audit
- `ReopenTaskServiceTest`: 9 tests — success, not found, not completed, archived, milestone completed, not owner, project not found, milestone not found, audit
- `UpdateTaskServiceTest`: +2 tests — COMPLETED rejection, allows after reopen

### Integration Tests

- `TaskStatusFlowIT`: complete/reopen DB state, list filtering (all/OPEN/COMPLETED/priority/combined/invalid), COMPLETED task restrictions, ARCHIVED project restrictions, COMPLETED milestone restrictions
- `TaskStatusSecurityIT`: anonymous, no CSRF, non-member, cross-workspace, cross-project, cross-milestone
- `TaskStatusAuditIT`: TASK_COMPLETED/TASK_REOPENED events, metadata content, no sensitive data
- `OpenApiTaskDocumentationIT`: complete/reopen endpoints, security schemes

---

## 15. Files Changed

### New Files

```
backend/src/main/java/com/milestoneflow/task/application/port/in/CompleteTaskUseCase.java
backend/src/main/java/com/milestoneflow/task/application/port/in/ReopenTaskUseCase.java
backend/src/main/java/com/milestoneflow/task/application/service/CompleteTaskService.java
backend/src/main/java/com/milestoneflow/task/application/service/ReopenTaskService.java
backend/src/main/java/com/milestoneflow/task/domain/exception/TaskAlreadyCompletedException.java
backend/src/main/java/com/milestoneflow/task/domain/exception/TaskCompletedException.java
backend/src/main/java/com/milestoneflow/task/domain/exception/TaskNotCompletedException.java
backend/src/test/java/com/milestoneflow/task/application/service/CompleteTaskServiceTest.java
backend/src/test/java/com/milestoneflow/task/application/service/ReopenTaskServiceTest.java
backend/src/test/java/com/milestoneflow/task/integration/TaskStatusFlowIT.java
backend/src/test/java/com/milestoneflow/task/integration/TaskStatusSecurityIT.java
backend/src/test/java/com/milestoneflow/task/integration/TaskStatusAuditIT.java
backend/docs/task/MF-BE-B5-002_TASK_STATUS_CLOSURE.md
```

### Modified Files

```
backend/src/main/java/com/milestoneflow/task/domain/model/Task.java
backend/src/main/java/com/milestoneflow/task/application/service/UpdateTaskService.java
backend/src/main/java/com/milestoneflow/task/api/TaskController.java
backend/src/main/java/com/milestoneflow/task/api/TaskExceptionHandler.java
backend/src/test/java/com/milestoneflow/task/domain/TaskTest.java
backend/src/test/java/com/milestoneflow/task/application/service/UpdateTaskServiceTest.java
backend/src/test/java/com/milestoneflow/task/integration/OpenApiTaskDocumentationIT.java
```

### Database Migration

```
V001–V010 unchanged. No new migration.
```

---

## 16. B5 Stage Completion

**Task B5 backend foundation completed.**

All B5 minimum completion criteria met:

- [x] Task schema (V010)
- [x] Task create / list / detail / update
- [x] Task complete / reopen
- [x] Task list status filter
- [x] Task list priority filter
- [x] Task ordering strategy
- [x] Milestone-scoped data isolation
- [x] Project-scoped data isolation
- [x] Workspace-scoped data isolation
- [x] Task audit
- [x] Task OpenAPI
- [x] Task tests
- [ ] CI clean verify (pending GitHub Actions)

**Progress calculation stage can start.**
