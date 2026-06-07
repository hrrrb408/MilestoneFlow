# Architecture Testing

## Purpose

MilestoneFlow uses [ArchUnit](https://archunit.org/) to enforce architectural boundaries at compile time. Architecture decisions documented in ADRs are codified as automated tests that prevent architectural drift.

ArchUnit tests run as part of the standard Surefire unit test suite alongside business tests — no separate step is required.

## Current Rules

All rules are defined in `ArchitectureRulesTest` under the `com.milestoneflow.architecture` test package.

### Minimum Rules (ADR-BE-001)

| ID | Rule | Rationale |
|----|------|-----------|
| ARCH-001 | `shared` must not depend on business modules | shared is a stable kernel providing cross-cutting infrastructure. Business modules depend on shared, never the reverse. |
| ARCH-002 | `domain` must not depend on `api` layer | Domain logic is independent of HTTP concerns. Controllers and DTOs are external adapters. |
| ARCH-003 | `domain` must not depend on Spring Web / Servlet API | Keeps domain portable and testable without a web container. |
| ARCH-004 | `api` must not directly access `repository` / `persistence` | Controllers go through application services, never bypass to data access. |
| ARCH-005 | Modules must not access other modules' `infrastructure` | Cross-module collaboration only via application facade, stable DTOs, IDs, or domain events. |
| ARCH-006 | Controllers must not return JPA entities | Controllers return DTOs. Entity-to-DTO mapping happens in application/domain layers. |

### Supplementary Rules

| ID | Rule | Rationale |
|----|------|-----------|
| ARCH-007 | Repository types must not reside in `api` | Repository interfaces belong in their module's domain or infrastructure layer. |
| ARCH-008 | `@Configuration` classes must end with `Configuration` or `Config` | Naming convention for discoverability. |
| ARCH-009 | `@RestController` classes must end with `Controller` | Naming convention for discoverability. |

## Package Structure Convention

```
com.milestoneflow/
├── MilestoneFlowApplication.java
├── shared/
│   ├── api/          # ApiResponse, ApiErrorResponse, PageMeta
│   ├── id/           # IdGenerator, UuidV7IdGenerator
│   ├── time/         # TimeConfiguration
│   └── web/          # GlobalExceptionHandler, RequestIdFilter
├── identity/         # (future)
│   ├── domain/
│   ├── application/
│   ├── api/
│   └── infrastructure/
├── workspace/        # (future)
│   ├── domain/
│   ├── application/
│   ├── api/
│   └── infrastructure/
└── ...               # other business modules
```

## How to Run Locally

```bash
cd backend
./mvnw test -pl . -Dtest="com.milestoneflow.architecture.*"
```

Or as part of the full test suite:

```bash
./mvnw clean test
```

## Adding New Modules

When a new business module is added (e.g., `project`):

1. Create the package: `com.milestoneflow.project`
2. Create internal layers: `domain`, `application`, `api`, `infrastructure`
3. The existing ArchUnit rules will automatically enforce boundaries for the new module
4. If the module introduces new patterns, add module-specific rules

## Handling Legitimate Exceptions

If a rule produces a false positive:

1. **Do not** add a global `@ArchIgnore` or disable the entire rule
2. **Do** document the specific exception in the test with a comment explaining why
3. Use ArchUnit's `allowEmptyShould` or scoped `that()` filters for the minimal exception
4. If the rule itself is wrong, update the ADR first, then update the test

## Rule Changes

Architecture rules must be changed in this order:

1. Update or create the relevant ADR in `backend/docs/adr/`
2. Get ADR approval through the architecture review process
3. Update the ArchUnit test to match the approved ADR

Rules must not be weakened to make a failing test pass. If a rule blocks legitimate work, the ADR should be updated first.

## Business Module List

The following business module names are recognised by the architecture tests:

`identity`, `workspace`, `client`, `project`, `quotation`, `baseline`, `delivery`, `changeorder`, `receivable`, `publicaccess`, `fileasset`, `notification`, `audit`, `actioncenter`, `pilotfeedback`, `scheduler`

Source: Architecture doc §02 and ADR-BE-001.
