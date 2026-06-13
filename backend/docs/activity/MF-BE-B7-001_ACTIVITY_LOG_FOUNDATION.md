# MF-BE-B7-001: ActivityLog Timeline APIs

## Status: In Progress

## 1. Task Scope

Add read-only activity timeline APIs that expose existing `audit_event` records as a product-friendly activity log. No new database tables, no new migrations.

## 2. ActivityLog and audit_event Relationship

ActivityLog is **not** a separate table. It is a read-only business view over the existing `audit_event` table:

```text
ActivityLog API → ActivityLogQueryRepository → SELECT FROM audit_event
```

The `audit_event` table (V004) already contains:
- `workspace_id` — workspace scope
- `actor_id` / `actor_type` — who performed the action
- `action` — event type (e.g., TASK_CREATED), exposed as `eventType` in the API
- `target_type` / `target_id` — what entity was affected
- `summary` — human-readable description
- `metadata` — additional context (JSONB, sanitised for sensitive keys on write)
- `created_at` — event timestamp

### Why no new activity_log table

1. audit_event already records all business operations (B1–B6)
2. audit_event already has workspace_id, actor_id, target_type, target_id, metadata, created_at
3. Adding a separate table would require dual-write or CDC, introducing consistency risk
4. Existing indexes (`idx_audit_event_workspace_time`, `idx_audit_event_target_time`) cover all query patterns
5. No migration needed — zero database changes

## 3. API Endpoints

### 3.1 Workspace Activity Timeline

```http
GET /api/v1/workspaces/{workspaceId}/activities?limit=20&eventType=TASK_CREATED&targetType=TASK
```

Returns all activity events for a workspace, optionally filtered by `eventType` and `targetType`.

### 3.2 Project Activity Timeline

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/activities?limit=20
```

Returns project-level events only (`target_type = 'PROJECT'`).

### 3.3 Milestone Activity Timeline

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/activities?limit=20
```

Returns milestone-level events only (`target_type = 'MILESTONE'`).

### 3.4 Task Activity Timeline

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/activities?limit=20
```

Returns task-level events (`target_type = 'TASK'`).

## 4. Scope Definition

### Safe Minimum Scope

| Scope | SQL Filter | Rationale |
|-------|-----------|-----------|
| Workspace | `workspace_id = :workspaceId` | All events in workspace |
| Project | `target_type = 'PROJECT' AND target_id = :projectId` | Only project lifecycle events |
| Milestone | `target_type = 'MILESTONE' AND target_id = :milestoneId` | Only milestone lifecycle events |
| Task | `target_type = 'TASK' AND target_id = :taskId` | Only task events |

### Why Project/Milestone scope doesn't include child events

Task and milestone audit events do **not** carry `projectId` in their metadata. Including child-entity events in a project timeline would require either:

- **Option A**: Join current task/milestone tables to infer project ownership — but deleted entities would lose their history
- **Option B**: Add `projectId` to all audit metadata — requires a migration of audit write patterns across B2–B5

For B7-001, we use the **safe minimum**: each scope only returns events where the `target_type`/`target_id` directly match. The workspace scope already shows the full timeline.

## 5. Response Format

```json
{
  "data": {
    "items": [
      {
        "id": "uuid",
        "workspaceId": "uuid",
        "actorId": "uuid",
        "actorType": "USER",
        "eventType": "TASK_COMPLETED",
        "targetType": "TASK",
        "targetId": "uuid",
        "summary": "Task completed",
        "metadata": { "previousStatus": "OPEN", "newStatus": "COMPLETED" },
        "createdAt": "2026-06-13T10:00:00Z"
      }
    ],
    "nextCursor": null
  },
  "meta": { "requestId": "..." }
}
```

`nextCursor` is reserved for future cursor-based pagination (B7-002) and is always `null` in B7-001.

## 6. Limit Strategy

- Default: 20
- Minimum: 1
- Maximum: 100 (clamped silently — no 422 error)

## 7. Filter Strategy

- `eventType` — filters on `audit_event.action` (e.g., `TASK_CREATED`, `PROJECT_UPDATED`)
- `targetType` — filters on `audit_event.target_type` (e.g., `TASK`, `MILESTONE`)
- Both filters are only available on the workspace scope endpoint
- Both are optional and nullable

## 8. Permission and Data Isolation

| Scenario | Result |
|----------|--------|
| Authenticated ACTIVE member | 200 — can read |
| Authenticated OWNER | 200 — can read |
| Non-member | 404 — WORKSPACE_NOT_FOUND |
| Anonymous | 401 — Unauthorized |
| Cross-workspace | 404 |
| Cross-project | 404 |
| Cross-milestone | 404 |
| Cross-task | 404 |
| ARCHIVED project | 200 — activities readable |
| COMPLETED milestone | 200 — activities readable |
| COMPLETED task | 200 — activities readable |

GET endpoints do not require CSRF token.

## 9. SQL Queries

All queries use `NamedParameterJdbcTemplate` with named parameters. No string concatenation of user input.

```sql
-- Workspace scope
SELECT id, workspace_id, actor_id, actor_type, action AS event_type,
       target_type, target_id, summary, metadata, created_at
  FROM audit_event
 WHERE workspace_id = :workspaceId
   AND (:eventType IS NULL OR action = :eventType)
   AND (:targetType IS NULL OR target_type = :targetType)
 ORDER BY created_at DESC, id DESC
 LIMIT :limit

-- Project scope (safe minimum)
SELECT ... FROM audit_event
 WHERE workspace_id = :workspaceId
   AND target_type = 'PROJECT' AND target_id = :projectId
 ORDER BY created_at DESC, id DESC LIMIT :limit
```

All queries are covered by existing indexes:
- `idx_audit_event_workspace_time(workspace_id, created_at DESC)` — workspace scope
- `idx_audit_event_target_time(target_type, target_id, created_at DESC)` — entity scope

## 10. OpenAPI

All endpoints documented with:
- `@Operation`, `@ApiResponses`, `@Tag`
- `cookieAuth` security scheme
- No JWT Bearer
- No csrfToken on GET
- `ActivityLogResponse` and `ActivityLogListResponse` schemas

## 11. Test Coverage

| Test Class | Coverage |
|------------|----------|
| ActivityLogFlowIT | Full lifecycle: create entities → verify activities appear, ordering, filters |
| ActivityLogSecurityIT | Anonymous → 401, non-member → 404, cross-scope → 404, CSRF not required |
| ActivityLogScopeIT | Two-workspace isolation, filter isolation |
| OpenApiActivityLogDocumentationIT | Endpoints, schemas, security, no JWT |

## 12. Module Structure

```text
com.milestoneflow.activity/
├── api/
│   ├── ActivityLogController.java
│   ├── ActivityLogExceptionHandler.java
│   └── dto/
│       ├── ActivityLogResponse.java
│       └── ActivityLogListResponse.java
├── application/
│   ├── port/in/
│   │   ├── ListWorkspaceActivitiesUseCase.java
│   │   ├── ListProjectActivitiesUseCase.java
│   │   ├── ListMilestoneActivitiesUseCase.java
│   │   └── ListTaskActivitiesUseCase.java
│   ├── port/out/
│   │   └── ActivityLogQueryRepository.java
│   ├── result/
│   │   └── ActivityLogRow.java
│   └── service/
│       ├── ListWorkspaceActivitiesService.java
│       ├── ListProjectActivitiesService.java
│       ├── ListMilestoneActivitiesService.java
│       └── ListTaskActivitiesService.java
└── infrastructure/persistence/
    └── ActivityLogQueryRepositoryAdapter.java
```

## 13. Explicitly Not Implemented

- Notification
- Comment
- Attachment
- Risk / Feedback
- Dashboard / Reports
- WebSocket / SSE / Subscription
- Email notifications
- Redis / ElasticSearch
- JWT / OAuth2
- Database migration
- Cursor-based pagination (reserved for B7-002)
- Activity aggregation / statistics
- Full-text search
- Export

## 14. Next Steps

```text
MF-BE-B7-002: ActivityLog cursor pagination, scope expansion, and B7 closure
  OR
MF-BE-B8-001: Risk / Feedback foundation (if B7-001 satisfies MVP)
```
