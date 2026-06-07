# ADR-BE-008: Flyway and JPA Startup Order

| Field | Value |
|-------|-------|
| Status | **Accepted** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |
| Decision Date | 2026-06-07 |
| Decision Makers | System Architect |
| Review Reference | ADR_REVIEW_REPORT.md §ADR-BE-008 |
| Note | Already implemented in B0. ddl-auto=validate confirmed. Executed V* migrations are immutable. |

## Background

Spring Boot runs Flyway before JPA/Hibernate initialization. With `ddl-auto=validate`, Hibernate validates that all entity-mapped tables and columns exist in the database. If Flyway has not created a table that an entity maps, the application fails to start. This ordering is critical for CI and deployment reliability.

## Constraints

1. `spring.jpa.hibernate.ddl-auto=validate` — never `create`, `create-drop`, or `update`.
2. Flyway must run before Hibernate validation.
3. Flyway migrations are immutable after creation.
4. New entities must have a corresponding Flyway migration before the entity is introduced.
5. Migration naming: `V001__description.sql`, `V002__description.sql`, etc.

## Options

### Option A: Rely on Spring Boot default ordering (Flyway → JPA)

Spring Boot already orders `Flyway` before `JPA` by default. No configuration change needed.

### Option B: Explicit `depends-on` or `@DependsOn` annotation

Configure the datasource or JPA to explicitly depend on Flyway.

### Option C: Separate Flyway execution as a build plugin step

Run Flyway migrations during build (before tests) via `flyway-maven-plugin`.

## Recommendation

**Option A: Rely on Spring Boot default ordering.**

Add an integration test that verifies: given a Flyway migration and a corresponding entity, the application starts without error. If a developer adds an entity without a migration, the `ApplicationStartupIT` will fail.

## Advantages

- No custom configuration — Spring Boot handles it.
- The `validate` mode acts as a safety net: schema mismatch = startup failure.
- Integration tests naturally catch missing migrations.

## Disadvantages and Risks

- If someone adds `spring.jpa.hibernate.ddl-auto=update` in a profile, the safety net is bypassed.
- Long migration history may slow startup (mitigated by keeping migrations focused).

## Impact on Tests

- `ApplicationStartupIT` already verifies startup with Flyway + JPA validate.
- Each new entity must be accompanied by a Flyway migration, or the IT fails.
- Consider adding a specific test that counts Flyway migrations and compares to entity count.

## Impact on Database

- Flyway migration files are the single source of truth for schema.
- `flyway_schema_history` table tracks all applied migrations.
- No Hibernate-generated DDL.

## Items for Architecture Window Confirmation

- [ ] Confirm `ddl-auto=validate` is acceptable (never auto-DDL).
- [ ] Confirm whether Flyway baseline-on-migrate should be enabled for existing environments.
- [ ] Confirm whether a Flyway checksum validation step should be added to CI.
