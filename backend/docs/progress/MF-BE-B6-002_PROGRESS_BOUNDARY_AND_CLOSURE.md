# MF-BE-B6-002: Progress Boundary Consistency, Performance Verification & B6 Closure

## 1. Task Scope

This is the **closure and stabilization task** for the B6 progress read model.
It adds no new business API and no schema changes. It hardens the B6-001 read
model by pinning its boundaries, verifying state-consistency behavior,
documenting its performance characteristics, and re-checking its SQL safety and
OpenAPI contract.

### Implemented in this task

- `ProgressRateCalculator` boundary hardening (defensive validation).
- `ProgressBoundaryIT` — exhaustive completion-rate ratio boundaries.
- `ProgressConsistencyIT` — status-inconsistency scenarios (read-only model).
- `ProgressPerformanceIT` — scale correctness over 200 tasks / 20 milestones.
- `OpenApiProgressDocumentationIT` — full contracted field-set assertions.
- `ProgressRateCalculatorTest` — 1/6, 5/6, 0/3, and defensive-throw coverage.
- This closure document.

### NOT implemented (out of scope, explicitly)

- ActivityLog / activity timeline
- Dashboard / workspace global statistics
- Risk query
- Feedback
- Notification / report / export
- Progress cache / snapshot / progress table / materialized view
- Scheduled recalculation
- Redis / ElasticSearch
- JWT / OAuth2
- Any database migration (V001–V010 untouched, no V011)
- Frontend pages / production deployment

---

## 2. No Database Migration

This task introduces **zero** database changes:

- V001–V010 are unmodified
- No V011
- No progress table / progress_snapshot / dashboard table
- No new indexes, materialized views, or triggers

All progress remains real-time task/milestone aggregation over the existing
`task` and `milestone` tables. No index tuning was required at this scale; if a
future very-large-workspace workload needs it, it should be a separate
pre-release performance task (e.g. a prospective `MF-BE-B6-003`), not this
closure task.

---

## 3. Final Progress Calculation Rules

### 3.1 Task dimension

```
totalTasks     = count(task)
completedTasks = count(task where status = COMPLETED)
openTasks      = count(task where status = OPEN)   // = totalTasks - completedTasks
completionRate = completedTasks / totalTasks * 100
```

Task status is restricted to `OPEN` / `COMPLETED` (see `TaskStatus`).

### 3.2 Milestone dimension

Milestone progress is the aggregation of that milestone's tasks:

```
milestone.totalTasks     = count(task where milestone_id = :milestoneId)
milestone.completedTasks = count(task where status = COMPLETED)
milestone.openTasks      = totalTasks - completedTasks
milestone.completionRate = completedTasks / totalTasks * 100
```

The milestone's own status does **not** enter this calculation (see §7).

### 3.3 Project dimension

Project progress is the aggregation of **all tasks** under the project:

```
project.totalTasks     = count(task where project_id = :projectId)
project.completedTasks = count(task where status = COMPLETED)
project.openTasks      = totalTasks - completedTasks
project.completionRate = completedTasks / totalTasks * 100
```

Milestone counts are reported alongside for context only:

```
totalMilestones     = count(milestone where project_id = :projectId)
completedMilestones = count(milestone where status = COMPLETED)
openMilestones      = count(milestone where status = OPEN)
```

### 3.4 Zero-task rule

When `totalTasks == 0`:

```
totalTasks = 0, completedTasks = 0, openTasks = 0, completionRate = 0.00
```

A milestone/project with no tasks is **never** treated as 100% complete.

### 3.5 completionRate precision

```
BigDecimal, scale = 2, RoundingMode.HALF_UP, returned as a number (not a string)
```

Verified values:

| completed / total | completionRate |
|-------------------|----------------|
| 0 / 0             | 0.00           |
| 0 / 3             | 0.00           |
| 1 / 1             | 100.00         |
| 1 / 2             | 50.00          |
| 1 / 3             | 33.33          |
| 2 / 3             | 66.67          |
| 1 / 6             | 16.67          |
| 5 / 6             | 83.33          |

---

## 4. Why Project Progress Is Not the Milestone Average

Different milestones carry different task counts, so a simple average of
milestone completion rates distorts true project progress:

- Milestone A: 3 tasks, 2 completed → 66.67%
- Milestone B: 2 tasks, 0 completed → 0.00%
- Milestone-rate average = (66.67 + 0.00) / 2 = **33.33%**
- Task aggregation = 2 / 5 = **40.00%** ← what the API returns

This holds even when a milestone is marked `COMPLETED`: project progress still
follows task aggregation, not milestone status or milestone-rate average.
Covered by `ProgressConsistencyIT.projectUsesTaskAggregationNotMilestoneAverage`.

---

## 5. Milestone Status vs. Progress

The read model is **read-only and non-repairing**. It reflects task state
as-is, so status inconsistencies surface faithfully rather than being masked:

- A milestone marked `COMPLETED` with open tasks reports its real task-based rate
  (e.g. 2/3 → 66.67, not 100.00).
- A milestone still `OPEN` whose tasks are all done reports 100.00.

The milestone status is included in the response (`milestoneStatus`) for
context, but it never drives `completionRate`. Covered by
`ProgressConsistencyIT.MilestoneStatusIndependence`.

---

## 6. Archived Project & Completed Milestone Read Policy

Progress is a read-only query:

- **ARCHIVED project**: progress is readable and correct (status does not block reads).
- **COMPLETED milestone**: progress is readable and correct.

Covered by `ProgressConsistencyIT.archivedProjectRemainsReadable` and the
existing `ProgressFlowIT.shouldWorkForArchivedProject`. The service flow checks
workspace membership and that the project/milestone belongs to the workspace,
but performs **no status gate** on project/milestone status before reading.

---

## 7. SQL Query Safety & Tenant Isolation

All queries use `NamedParameterJdbcTemplate` with **named parameters** — no
string concatenation of user input. Every query scopes by tenant keys:

| Query | WHERE filter |
|-------|--------------|
| Project task counts | `workspace_id = :workspaceId AND project_id = :projectId` |
| Project milestone counts | `workspace_id = :workspaceId AND project_id = :projectId` |
| Milestone task counts | `workspace_id = :workspaceId AND project_id = :projectId AND milestone_id = :milestoneId` |
| Milestone progress list | `m.workspace_id = :workspaceId AND m.project_id = :projectId` |

The milestone-list LEFT JOIN is keyed on the **full composite** condition
(`t.milestone_id = m.id AND t.workspace_id = m.workspace_id AND t.project_id = m.project_id`),
never on `milestone_id` alone.

Isolation is verified end-to-end by `ProgressSecurityIT` (cross-workspace,
cross-project, cross-milestone all return 404) — per the spec, integration-level
isolation verification is preferred over brittle SQL-string unit assertions.

---

## 8. Performance Verification

`ProgressPerformanceIT` provisions a representative workload via direct JDBC:

- 1 project, 20 milestones (mixed due dates to exercise `NULLS LAST` ordering),
  200 tasks (5 COMPLETED + 5 OPEN per milestone).

Both endpoints return correct aggregates at that scale:

- Project progress: `totalTasks=200`, `completedTasks=100`, `completionRate=50.00`,
  `totalMilestones=20`.
- Milestone progress list: 20 entries, each `totalTasks=10, completedTasks=5,
  completionRate=50.00`; summed totals match the project totals.

**No strict millisecond threshold is asserted** (CI timing is too noisy). Elapsed
time is printed to stdout for observability only.

**No N+1.** Both code paths are single aggregate statements:
- Project progress = one `COUNT(*) FILTER(...)` scan over `task` + one over
  `milestone` (`countTasksByProject`, `countMilestonesByProject`).
- Milestone progress list = one `milestone LEFT JOIN task` with conditional
  aggregates grouped per milestone (`countTasksPerMilestone`) — explicitly not
  one query per milestone.

This is enforced by code structure (`ProgressQueryRepositoryAdapter`) rather
than a datasource-proxy query counter, which would add test infrastructure out
of scope for this closure task.

---

## 9. Boundary Hardening (the one production change)

`ProgressRateCalculator.calculate(long completedTasks, long totalTasks)` now
rejects inconsistent inputs up front so a completion rate above 100.00 can never
be produced:

```
completedTasks < 0                  → IllegalArgumentException
totalTasks     < 0                  → IllegalArgumentException
completedTasks > totalTasks         → IllegalArgumentException   // incl. (n, 0)
totalTasks     == 0 (and completedTasks == 0) → 0.00
otherwise                           → completedTasks / totalTasks * 100, scale 2 HALF_UP
```

The division and rounding themselves are unchanged from B6-001. This is a
minimal, defense-in-depth change — covered by `ProgressRateCalculatorTest`.
The application services always feed valid counts (`completedTasks` is a
`COUNT(*) FILTER (WHERE status='COMPLETED')`, a subset of `totalTasks`), so the
guards are belt-and-braces against future inconsistent source data.

---

## 10. API Response Contract

Responses carry only the contracted fields. Internal/audit fields are absent.

**ProjectProgressResponse:** `workspaceId, projectId, totalTasks, completedTasks,
openTasks, completionRate, totalMilestones, completedMilestones, openMilestones`.

**MilestoneProgressResponse:** `workspaceId, projectId, milestoneId,
milestoneTitle, milestoneStatus, totalTasks, completedTasks, openTasks,
completionRate`.

**MilestoneProgressListResponse:** `items` (list of `MilestoneProgressResponse`).

Invariants asserted: `openTasks + completedTasks == totalTasks`,
`openMilestones + completedMilestones == totalMilestones`, and
`completionRate ∈ [0.00, 100.00]` (the calculator guard makes > 100.00
impossible). Verified absent: `createdBy, updatedBy, completedBy, settings,
version, createdAt, updatedAt` (OpenAPI + flow tests).

---

## 11. OpenAPI Regression

`OpenApiProgressDocumentationIT` now asserts:

- All three progress paths exist with `GET` operations.
- `cookieAuth` security scheme is present; no JWT Bearer.
- `GET` endpoints declare no `csrfToken`.
- Each response schema exposes **exactly** its contracted field set
  (`containsExactlyInAnyOrder`).
- `MilestoneProgressListResponse` exposes only `items`.
- No internal/audit fields leak into either progress response schema.

---

## 12. Test Coverage

### Unit

| Test class | Coverage |
|-----------|----------|
| `ProgressRateCalculatorTest` | 0/0, 0/3, 1/1, 1/2, 1/3, 2/3, 1/6, 5/6, 3/7, 4/10, scale=2; negative/`completed>total` throws |
| `GetProjectProgressServiceTest` | Project aggregation, access checks, 404 (unchanged, still green) |
| `GetMilestoneProgressServiceTest` | Milestone aggregation, access checks, 404 (unchanged) |
| `ListMilestoneProgressServiceTest` | List with zero-task milestones, access checks (unchanged) |

### Integration

| Test class | Coverage |
|-----------|----------|
| `ProgressFlowIT` | Full stack: project/milestone/list, accuracy, archived, 0% |
| `ProgressSecurityIT` | Anonymous 401, non-member 404, cross-ws/project/milestone 404, CSRF |
| `ProgressBoundaryIT` (**new**) | Exact ratios (1/1, 1/2, 1/3, 2/3, 1/6, 5/6), 0-task, 0-milestone, count invariants |
| `ProgressConsistencyIT` (**new**) | Milestone status ≠ rate, archived readable, project ≠ milestone average |
| `ProgressPerformanceIT` (**new**) | 200 tasks / 20 milestones scale correctness, no-N+1 documentation |
| `OpenApiProgressDocumentationIT` | Endpoint existence, security scheme, exact field sets, no leakage |

---

## 13. Explicitly Out of Scope

ActivityLog, Dashboard, workspace global stats, Risk query, Feedback,
Notification, Report, Export, progress cache/snapshot/table, materialized view,
scheduled recalculation, Redis, ElasticSearch, JWT, OAuth2, database migration,
frontend, production deployment.

---

## 14. B6 Stage Completion Verdict

All B6-002 acceptance criteria are met:

- Boundary, consistency, and performance tests added and green locally (unit)
  and on CI (integration via Testcontainers + PostgreSQL 17).
- 0-task → 0.00, 1/3 → 33.33, 2/3 → 66.67 verified.
- Project progress is task-aggregated, not milestone-averaged, verified.
- Milestone status does not drive completionRate, verified.
- ARCHIVED project and COMPLETED milestone remain readable, verified.
- SQL workspace/project/milestone isolation verified.
- OpenAPI regression green; V001–V010 unmodified; no new migration.
- No ActivityLog / Dashboard / Risk / Feedback introduced.

---

## 15. Next Stage

```
Progress B6 backend read model completed.
ActivityLog stage can start.
```

The next backend stage may begin ActivityLog auto-recording and timeline query
foundation (e.g. `MF-BE-B7-001`).
