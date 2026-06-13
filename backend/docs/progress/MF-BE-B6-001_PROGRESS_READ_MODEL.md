# MF-BE-B6-001: Progress Read Model

## 1. Task Scope

Implement read-only progress calculation APIs for MilestoneFlow. This module provides real-time completion rate calculation based on existing task data, without any database schema changes.

### What is implemented

- Project progress query (task aggregation + milestone counts)
- Milestone progress query (task aggregation per milestone)
- Milestone progress list query (all milestones in a project)
- Progress rate calculator utility (BigDecimal, 2 decimal places)
- Full ArchUnit compliance
- Integration tests: flow, security, accuracy, OpenAPI

### What is NOT implemented

- Dashboard / workspace global statistics
- Activity timeline
- Risk query
- Feedback
- Progress cache / snapshot / materialized view
- Scheduled recalculation
- Any database migration
- Frontend pages

---

## 2. No Database Migration

This module introduces **zero** database changes:

- V001–V010 are unmodified
- No V011
- No progress table
- No progress_snapshot table
- No dashboard table
- No new indexes
- No materialized views
- No triggers

All progress is calculated in real-time from the existing `task` and `milestone` tables using PostgreSQL conditional aggregates.

---

## 3. Progress APIs

### 3.1 Project Progress

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/progress
```

Returns aggregated task and milestone counts for a project.

**Response:**
```json
{
  "data": {
    "workspaceId": "uuid",
    "projectId": "uuid",
    "totalTasks": 10,
    "completedTasks": 4,
    "openTasks": 6,
    "completionRate": 40.00,
    "totalMilestones": 3,
    "completedMilestones": 1,
    "openMilestones": 2
  },
  "meta": { "requestId": "..." }
}
```

### 3.2 Milestone Progress

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/progress
```

Returns task counts and completion rate for a specific milestone.

**Response:**
```json
{
  "data": {
    "workspaceId": "uuid",
    "projectId": "uuid",
    "milestoneId": "uuid",
    "milestoneTitle": "Backend MVP",
    "milestoneStatus": "OPEN",
    "totalTasks": 5,
    "completedTasks": 2,
    "openTasks": 3,
    "completionRate": 40.00
  },
  "meta": { "requestId": "..." }
}
```

### 3.3 Milestone Progress List

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/progress
```

Returns progress for all milestones in a project. Ordered by due date ASC (nulls last), then creation time ASC.

**Response:**
```json
{
  "data": {
    "items": [
      {
        "workspaceId": "uuid",
        "projectId": "uuid",
        "milestoneId": "uuid",
        "milestoneTitle": "Backend MVP",
        "milestoneStatus": "OPEN",
        "totalTasks": 3,
        "completedTasks": 2,
        "openTasks": 1,
        "completionRate": 66.67
      }
    ]
  },
  "meta": { "requestId": "..." }
}
```

### 3.4 Not Implemented APIs

The following are explicitly out of scope:

```http
GET /api/v1/workspaces/{workspaceId}/progress          — Workspace global stats
GET /api/v1/dashboard                                   — Dashboard
GET /api/v1/workspaces/{workspaceId}/dashboard          — Workspace dashboard
GET /api/v1/reports/*                                   — Reports
POST /api/v1/progress/recalculate                       — Recalculation trigger
```

---

## 4. Completion Rate Calculation Rules

### 4.1 Formula

```
completionRate = completedTasks / totalTasks × 100
```

Scaled to 2 decimal places with HALF_UP rounding.

### 4.2 Zero Task Rule

When `totalTasks == 0`:

```
completionRate = 0.00
```

A milestone/project with no tasks is **never** treated as 100% complete.

### 4.3 Project Aggregation Strategy

Project progress uses **task-level aggregation** across all milestones, NOT milestone average.

**Rationale:** Different milestones may have different numbers of tasks. A simple average of milestone completion rates would distort the true project progress. For example:
- Milestone A: 3 tasks, 2 completed → 66.67%
- Milestone B: 2 tasks, 0 completed → 0.00%
- Average would be 33.33%, but actual progress is 2/5 = 40.00%

### 4.4 Milestone Independence

Each milestone's progress is calculated independently based on its own tasks. The milestone's own status (OPEN/COMPLETED) does not directly affect the completion rate. This allows detection of status inconsistencies (e.g., milestone marked COMPLETED but tasks still OPEN).

### 4.5 Precision

All `completionRate` values are `BigDecimal` with scale 2.

Examples:
- 0/0 → 0.00
- 1/1 → 100.00
- 1/2 → 50.00
- 1/3 → 33.33
- 2/3 → 66.67
- 4/10 → 40.00

---

## 5. Permissions and Data Isolation

### 5.1 Authentication

All progress endpoints require authentication via MF_ACCESS cookie.

### 5.2 Authorization

| User Type | Access |
|-----------|--------|
| ACTIVE member | ✅ Can read progress |
| OWNER | ✅ Can read progress |
| Non-member | ❌ 404 |
| PENDING member | ❌ 404 |
| REMOVED member | ❌ 404 |
| Anonymous | ❌ 401 |

### 5.3 Data Isolation

All SQL queries include `workspace_id` (and `project_id`, `milestone_id` as applicable) to prevent cross-tenant data leakage:

- Cross-workspace access → 404
- Cross-project access → 404
- Cross-milestone access → 404

404 (not 403) is returned for all unauthorized access to prevent resource existence leakage.

### 5.4 Status-Based Read Policy

- **ARCHIVED projects**: Progress is readable
- **COMPLETED milestones**: Progress is readable
- **COMPLETED tasks**: Counted in `completedTasks`
- **OPEN tasks**: Counted in `openTasks`

### 5.5 CSRF

GET endpoints do not require CSRF tokens.

---

## 6. Architecture

### 6.1 Module Structure

```
com.milestoneflow.progress/
├── api/
│   ├── ProgressController.java
│   ├── ProgressExceptionHandler.java
│   └── dto/
│       ├── ProjectProgressResponse.java
│       ├── MilestoneProgressResponse.java
│       └── MilestoneProgressListResponse.java
├── application/
│   ├── port/in/
│   │   ├── GetProjectProgressUseCase.java
│   │   ├── GetMilestoneProgressUseCase.java
│   │   └── ListMilestoneProgressUseCase.java
│   ├── port/out/
│   │   └── ProgressQueryRepository.java
│   ├── result/
│   │   ├── ProjectProgressResult.java
│   │   └── MilestoneProgressResult.java
│   └── service/
│       ├── ProgressRateCalculator.java
│       ├── GetProjectProgressService.java
│       ├── GetMilestoneProgressService.java
│       └── ListMilestoneProgressService.java
└── infrastructure/persistence/
    └── ProgressQueryRepositoryAdapter.java
```

### 6.2 Key Design Decisions

1. **No domain package**: Pure read model with no entities, no state, no domain exceptions
2. **No audit events**: Read-only queries do not produce audit trails
3. **NamedParameterJdbcTemplate**: More appropriate than JPA for aggregate count queries
4. **PostgreSQL FILTER clause**: Single-scan conditional counting (`COUNT(*) FILTER (WHERE ...)`)
5. **Existing exceptions reused**: ProjectNotFoundException, MilestoneNotFoundException, WorkspaceAccessDeniedException

### 6.3 Dependencies

```
progress → workspace.application (WorkspaceAccessChecker)
progress → project.application.port.out (ProjectRepository)
progress → milestone.application.port.out (MilestoneRepository)
progress → shared (ApiResponse, CurrentUserPrincipal, GlobalExceptionHandler)
```

---

## 7. SQL Queries

### 7.1 Project Task Counts

```sql
SELECT COUNT(*) AS total_tasks,
       COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
  FROM task t
 WHERE t.workspace_id = :workspaceId
   AND t.project_id = :projectId
```

### 7.2 Project Milestone Counts

```sql
SELECT COUNT(*) AS total_milestones,
       COUNT(*) FILTER (WHERE m.status = 'COMPLETED') AS completed_milestones,
       COUNT(*) FILTER (WHERE m.status = 'OPEN') AS open_milestones
  FROM milestone m
 WHERE m.workspace_id = :workspaceId
   AND m.project_id = :projectId
```

### 7.3 Milestone Task Counts

```sql
SELECT COUNT(*) AS total_tasks,
       COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
  FROM task t
 WHERE t.workspace_id = :workspaceId
   AND t.project_id = :projectId
   AND t.milestone_id = :milestoneId
```

### 7.4 Milestone Progress List

```sql
SELECT m.id AS milestone_id, m.title, m.status,
       COUNT(t.id) AS total_tasks,
       COUNT(t.id) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
  FROM milestone m
  LEFT JOIN task t
         ON t.milestone_id = m.id
        AND t.workspace_id = m.workspace_id
        AND t.project_id = m.project_id
 WHERE m.workspace_id = :workspaceId
   AND m.project_id = :projectId
 GROUP BY m.id, m.title, m.status, m.due_date, m.created_at
 ORDER BY CASE WHEN m.due_date IS NULL THEN 1 ELSE 0 END,
          m.due_date ASC, m.created_at ASC
```

The LEFT JOIN ensures milestones with zero tasks are included (completion rate = 0.00%).

---

## 8. OpenAPI Documentation

All three endpoints are documented with:

- `summary` and `description`
- Path parameters (`workspaceId`, `projectId`, `milestoneId`)
- 200 response with schema
- 401 response
- 404 response
- `cookieAuth` security scheme
- No JWT Bearer
- No csrfToken on GET endpoints

Schemas: `ProjectProgressResponse`, `MilestoneProgressResponse`, `MilestoneProgressListResponse`

---

## 9. Test Coverage

### Unit Tests

| Test Class | Coverage |
|-----------|----------|
| `ProgressRateCalculatorTest` | Rate calculation edge cases (0 tasks, 1/3, 2/3, precision) |
| `GetProjectProgressServiceTest` | Project aggregation, access checks, 404 cases |
| `GetMilestoneProgressServiceTest` | Milestone aggregation, access checks, 404 cases |
| `ListMilestoneProgressServiceTest` | List with zero-task milestones, access checks |

### Integration Tests

| Test Class | Coverage |
|-----------|----------|
| `ProgressFlowIT` | Full stack: project/milestone/list progress, accuracy, archived projects, 0% edge case |
| `ProgressSecurityIT` | Anonymous 401, non-member 404, cross-workspace/project/milestone 404, CSRF not required |
| `OpenApiProgressDocumentationIT` | Endpoint existence, cookieAuth, no JWT, schema presence |

---

## 10. Next Steps

**MF-BE-B6-002:** Progress boundary consistency, performance verification, and B6 stage closure.

Or if B6-001 is sufficient:

**MF-BE-B7-001:** ActivityLog auto-recording and timeline query foundation.
