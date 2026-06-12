# MF-BE-B5-001: Task Foundation CRUD

## Task Scope

Milestone-scoped Task backend foundation with full CRUD, data isolation, and audit.

### Implemented

| Feature | API | Status |
|---------|-----|--------|
| Task database schema | V010__task.sql | ✅ |
| Task Entity + enums | Task, TaskStatus, TaskPriority | ✅ |
| Create Task | POST /workspaces/{wsId}/projects/{pId}/milestones/{mId}/tasks | ✅ |
| List Tasks | GET /workspaces/{wsId}/projects/{pId}/milestones/{mId}/tasks | ✅ |
| Get Task Detail | GET /workspaces/{wsId}/projects/{pId}/milestones/{mId}/tasks/{tId} | ✅ |
| Update Task | PATCH /workspaces/{wsId}/projects/{pId}/milestones/{mId}/tasks/{tId} | ✅ |
| Milestone-scoped isolation | Composite key queries | ✅ |
| Project-scoped isolation | Composite key queries | ✅ |
| Workspace-scoped isolation | Composite key queries | ✅ |
| Task permissions | OWNER write, ACTIVE member read | ✅ |
| Task audit | TASK_CREATED, TASK_UPDATED | ✅ |
| Task OpenAPI | Full documentation | ✅ |
| Task unit tests | Domain + Service | ✅ |
| Task integration tests | Flow, Security, Constraint, Audit, OpenAPI | ✅ |

### Explicitly NOT Implemented

| Feature | Reason |
|---------|--------|
| Task complete/reopen API | B5-002 |
| Task status workflow | B5-002 |
| Progress calculation | Future |
| Milestone progress stats | Future |
| Project progress stats | Future |
| Dashboard | Future |
| Activity Timeline API | Future |
| Comment | Future |
| Attachment | Future |
| Notification | Future |
| Task delete | Future |
| Task archive | Future |
| Task drag-and-drop reorder | Future |
| Multi-person assignment | Future |
| Redis / JWT / OAuth2 | N/A |

---

## Database Schema

### V010__task.sql

```sql
CREATE TABLE task (
    id              uuid PRIMARY KEY,
    workspace_id    uuid NOT NULL REFERENCES workspace(id),
    project_id      uuid NOT NULL REFERENCES project(id),
    milestone_id    uuid NOT NULL REFERENCES milestone(id),
    title           varchar(160) NOT NULL,
    description     varchar(4000),
    status          varchar(32) NOT NULL DEFAULT 'OPEN',
    priority        varchar(32) NOT NULL DEFAULT 'MEDIUM',
    due_date        date,
    completed_at    timestamptz,
    completed_by    uuid REFERENCES app_user(id),
    settings        jsonb NOT NULL DEFAULT '{}',
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES app_user(id),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES app_user(id),
    CHECK (status IN ('OPEN', 'COMPLETED')),
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CHECK (jsonb_typeof(settings) = 'object')
);
```

### Indexes

- `idx_task_milestone_status_due` — milestone-scoped listing by status, due date
- `idx_task_workspace_project_milestone` — composite FK lookup
- `idx_task_workspace_created` — workspace-scoped by creation time

---

## Domain Design

### Task Entity
- Extends `AuditedEntity` (UUID id, createdAt, updatedAt, createdBy, updatedBy)
- FK stored as UUID (per ADR-BE-006, no @ManyToOne)
- `@Version` optimistic locking
- `settings` excluded from `toString()`
- Static factory: `Task.create(id, workspaceId, projectId, milestoneId, title, description, priority, dueDate)`
- Domain method: `updateBasicInfo(title, description, priority, dueDate)`

### TaskStatus Enum
- `OPEN` — initial status
- `COMPLETED` — reserved for B5-002

### TaskPriority Enum
- `LOW`, `MEDIUM`, `HIGH`
- Default: `MEDIUM`

### Constraints
- title: NOT NULL, max 160 chars, not blank
- description: max 4000 chars
- status: CHECK (OPEN, COMPLETED)
- priority: CHECK (LOW, MEDIUM, HIGH)
- settings: jsonb object

---

## API Design

### Create Task
```
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks
```
- Permission: OWNER
- Status: 201 Created
- Rejects: archived project (409), completed milestone (409), blank title (422)

### List Tasks
```
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks
```
- Permission: ACTIVE member
- Query params: `status`, `priority` (both optional)
- Readable under archived projects and completed milestones

### Get Task Detail
```
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}
```
- Permission: ACTIVE member
- Cross-workspace/project/milestone returns 404

### Update Task
```
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}
```
- Permission: OWNER
- Rejects: archived project (409), completed milestone (409)
- All fields optional (null = skip)

### Sorting Strategy
1. OPEN status before COMPLETED
2. Due date ASC NULLS LAST
3. Priority: HIGH → MEDIUM → LOW
4. Creation time ASC

### Error Codes
| Code | HTTP | Meaning |
|------|------|---------|
| TASK_NOT_FOUND | 404 | Task not found or cross-scope |
| TASK_INVALID_STATUS | 422 | Invalid status filter |
| TASK_INVALID_PRIORITY | 422 | Invalid priority value |
| PROJECT_ARCHIVED | 409 | Project is archived |
| MILESTONE_COMPLETED | 409 | Milestone is completed |
| WORKSPACE_OWNER_REQUIRED | 403 | Non-owner write attempt |

---

## Permissions & Data Isolation

| Role | Read | Write |
|------|------|-------|
| OWNER | ✅ | ✅ |
| ACTIVE member | ✅ | ❌ (403) |
| Non-member | ❌ (404) | ❌ (404) |
| Anonymous | ❌ (401) | ❌ (401) |

- Cross-workspace: 404
- Cross-project: 404
- Cross-milestone: 404
- Archived project: read OK, write blocked
- Completed milestone: read OK, write blocked

---

## Audit Events

| Event | Target Type | Metadata |
|-------|------------|----------|
| TASK_CREATED | TASK | title, priority |
| TASK_UPDATED | TASK | titleChanged, descriptionChanged, priorityChanged, dueDateChanged |

- Metadata does NOT contain: description text, email, password, token, secret
- All events include: workspace_id, actor_id, actor_type=USER, target_id=taskId

---

## Test Coverage

### Unit Tests (684 total, 0 failures)
- TaskTest: 17 tests (create, update, validation, toString)
- CreateTaskServiceTest: 9 tests
- ListTasksServiceTest: 9 tests
- GetTaskServiceTest: 5 tests
- UpdateTaskServiceTest: 9 tests
- ArchUnit: 10 architecture rules

### Integration Tests
- TaskFlowIT: CRUD flows, filters, cross-scope 404, archived/completed read
- TaskSecurityIT: anonymous 401, CSRF, non-member 404, cross-scope isolation
- TaskConstraintIT: CHECK constraints, FK constraints, Flyway V010
- TaskAuditIT: TASK_CREATED, TASK_UPDATED, no description in metadata
- OpenApiTaskDocumentationIT: endpoints, schemas, security, no JWT

---

## Next Step

MF-BE-B5-002: Task complete/reopen, status filtering, and B5 phase closure.
