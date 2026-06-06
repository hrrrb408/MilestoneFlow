# ADR-BE-006: Composite Foreign Key Mapping for Tenant Isolation

| Field | Value |
|-------|-------|
| Status | **Proposed** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |

## Background

All business tables in MilestoneFlow include `workspace_id uuid NOT NULL` for tenant isolation (architecture doc §02, POC-002). Parent-child relationships use composite foreign keys: `FOREIGN KEY (workspace_id, parent_id) REFERENCES parent(workspace_id, id)`. This prevents cross-tenant references at the database level.

## Constraints

1. Every business table has `workspace_id uuid NOT NULL`.
2. Parent-child FK: `FOREIGN KEY (workspace_id, project_id) REFERENCES project(workspace_id, id)`.
3. Parent tables must have `UNIQUE(workspace_id, id)` — PostgreSQL PK on `id` alone is not sufficient for composite FK targets.
4. Repository methods must always include `workspaceId` as the first parameter.
5. No bare `findById()` methods on business repositories.

## Options

### Option A: JPA `@ManyToOne` with `@JoinColumns`

```java
@ManyToOne(fetch = LAZY)
@JoinColumns({
    @JoinColumn(name = "workspace_id", referencedColumnName = "workspace_id", insertable = false, updatable = false),
    @JoinColumn(name = "project_id", referencedColumnName = "id", insertable = false, updatable = false)
})
private Project project;

@Column(name = "workspace_id", nullable = false)
private UUID workspaceId;

@Column(name = "project_id", nullable = false)
private UUID projectId;
```

### Option B: Store only IDs, no JPA relationships

```java
@Column(name = "workspace_id", nullable = false)
private UUID workspaceId;

@Column(name = "project_id", nullable = false)
private UUID projectId;
// No @ManyToOne — load parent via separate repository call
```

### Option C: Hibernate `@ManyToOne` with `@ForeignKey` + generated schema

Let Hibernate generate the DDL with composite FK constraints.

## Recommendation

**Option B: Store only IDs, no JPA entity relationships.**

Rationale: Explicit ID fields give full control over FK column naming and avoid lazy-loading surprises across module boundaries. Loading parent entities through separate repository calls respects the module isolation rule (no cross-module entity access). The composite FK constraint is still enforced at the database level via Flyway DDL.

## Advantages

- No lazy-loading exceptions or N+1 problems.
- Clean module boundaries — no hidden entity graph traversals.
- Composite FK constraints still enforced at DB level.
- Easier to test — no need to persist entire entity graphs.

## Disadvantages and Risks

- No cascade operations through JPA (must handle manually).
- Application must validate parent existence before insert (DB FK catches violations anyway).
- Slightly more verbose repository queries.

## Impact on Tests

- Verify composite FK rejects cross-tenant references (Testcontainers).
- Verify repository queries include `workspaceId` parameter.
- ArchUnit test: no JPA `@ManyToOne` / `@OneToMany` between modules.

## Impact on Database

- Flyway migration for each child table includes:
  ```sql
  workspace_id uuid NOT NULL,
  project_id uuid NOT NULL,
  FOREIGN KEY (workspace_id, project_id) REFERENCES project(workspace_id, id)
  ```
- Parent table: `UNIQUE(workspace_id, id)` as a secondary unique constraint (PK is still `id` alone).

## Items for Architecture Window Confirmation

- [ ] Confirm PK is `id uuid` alone (not `(workspace_id, id)` composite PK).
- [ ] Confirm whether `UNIQUE(workspace_id, id)` should be the PK instead.
- [ ] Confirm whether cross-module lazy loading is ever acceptable (likely not).
