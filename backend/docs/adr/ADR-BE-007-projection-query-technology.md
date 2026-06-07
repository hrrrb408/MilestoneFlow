# ADR-BE-007: Projection and Query Technology

| Field | Value |
|-------|-------|
| Status | **Accepted** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |
| Decision Date | 2026-06-07 |
| Decision Makers | System Architect |
| Review Reference | ADR_REVIEW_REPORT.md §ADR-BE-007 |
| Note | Not needed in B1. JPQL DTO for simple queries, native SQL for complex. jOOQ deferred to V0.2+. |

## Background

MilestoneFlow has two distinct read patterns: (1) aggregate loading for write operations (full entities), and (2) list/page/dashboard queries for read operations (projections). Dashboard queries (action center, feedback view) may span multiple aggregates and require denormalized views. The architecture doc §02 mentions action center and feedback view as projection modules.

## Constraints

1. Write side: load full aggregates via JPA repositories.
2. Read side: list views, pagination, dashboard may need lightweight DTOs.
3. Pagination: page-based (`?page=0&size=20&sort=createdAt,desc`), max page size 100.
4. No cross-module JPQL joins on entities.
5. Complex dashboard projections may need native SQL.

## Options

### Option A: Spring Data JPA Projections (Interface-based)

```java
public interface ProjectSummary {
    UUID getId();
    String getName();
    String getStatus();
    OffsetDateTime getCreatedAt();
}
```

### Option B: Spring Data JPA with `@Query` + DTO records

```java
@Query("SELECT new com.milestoneflow.project.application.ProjectSummary(p.id, p.name, ...) FROM Project p WHERE p.workspaceId = :wsId")
List<ProjectSummary> findSummaries(@Param("wsId") UUID workspaceId);
```

### Option C: JOOQ or MyBatis for read queries

Use a dedicated query library for complex read-side queries.

### Option D: Native SQL via Spring Data `@Query(nativeQuery = true)`

```java
@Query(value = "SELECT id, name, status FROM project WHERE workspace_id = :wsId", nativeQuery = true)
List<Object[]> findRawSummaries(@Param("wsId") UUID workspaceId);
```

## Recommendation

**Option B as default; Option D for complex dashboard queries.**

Rationale: Spring Data JPA record-based DTOs are type-safe and integrate with the existing Spring Data infrastructure. For simple list views, JPQL DTOs are sufficient. For complex dashboard projections that span multiple tables, native SQL provides full control without fighting JPQL limitations.

## Advantages

- No additional dependencies for the common case.
- Record-based DTOs are immutable and type-safe.
- Native SQL escape hatch available for complex queries.
- Consistent pagination through Spring Data `Pageable`.

## Disadvantages and Risks

- JPQL DTO constructor expressions are verbose for many fields.
- Native SQL results need manual mapping.
- No compile-time query validation for native SQL.

## Impact on Tests

- DTO projection tests: verify correct field mapping.
- Native SQL tests: verify against Testcontainers PostgreSQL.
- Pagination tests: verify page boundaries and sorting.

## Impact on Database

- Complex projections may benefit from database views (created via Flyway `R__` migrations).
- Dashboard queries may need composite indexes for sorting and filtering.

## Items for Architecture Window Confirmation

- [ ] Confirm whether database views are acceptable for complex projections.
- [ ] Confirm whether JOOQ or similar should be considered for V0.2+.
- [ ] Confirm cursor-based pagination vs page-based for large datasets.
