# ADR-BE-001: Package and Module Structure

| Field | Value |
|-------|-------|
| Status | **Proposed** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |

## Background

MilestoneFlow adopts a modular monolith architecture (architecture doc §01). Each business domain is an isolated module with internal implementation details hidden from other modules. The package structure must enforce module boundaries at compile time and support a future extraction to separate services if needed.

The architecture doc §02 defines 16 business modules plus a shared kernel.

## Constraints

1. Cross-module access is only allowed through public Application Service APIs or domain events.
2. No cross-module JPA Entity or Repository access.
3. Spring Modulith 1.4.x may be introduced later for compile-time boundary verification.
4. Package naming must align with the frozen architecture doc module list.

## Options

### Option A: Flat package per module (domain/application/api/infrastructure inside each module)

```
com.milestoneflow.project.domain
com.milestoneflow.project.application
com.milestoneflow.project.api
com.milestoneflow.project.infrastructure
```

### Option B: Nested package with explicit `internal` boundary

```
com.milestoneflow.project
com.milestoneflow.project.internal.domain   (package-private by convention)
com.milestoneflow.project.internal.repository
com.milestoneflow.project.application       (public API)
com.milestoneflow.project.api
```

### Option C: Spring Modulith formal modules with `@NamedInterface`

Use Spring Modulith annotations to declare explicitly which packages are public vs internal.

## Recommendation

**Option A** with an ArchUnit rule enforcing that no class in module X depends on `*.domain` or `*.infrastructure` packages of module Y.

Rationale: Option A is the simplest structure that matches the architecture doc. ArchUnit provides compile-time boundary checks without the complexity of Spring Modulith at this stage. Spring Modulith can be introduced later as an additive change.

## Advantages

- Direct mapping from architecture doc §02 module list.
- ArchUnit tests can enforce boundaries in CI.
- Clear separation of concerns per module.
- Easy to navigate: one module = one top-level package.

## Disadvantages and Risks

- Relies on team discipline and ArchUnit coverage — no JVM-level enforcement.
- Package-private classes in `domain` may still be accessed if someone adds a public accessor.
- Spring Modulith adoption later requires refactoring to `@NamedInterface`.

## Impact on Tests

- ArchUnit test suite must be added to verify module boundary rules.
- Test packages mirror main packages: `com.milestoneflow.project.application` tests do not import `com.milestoneflow.quotation.domain`.

## Impact on Database

- No direct database impact. Module boundaries are a code-level concern.
- Each module owns its Flyway migration files (future consideration).

## Items for Architecture Window Confirmation

- [ ] Confirm ArchUnit is acceptable as the boundary enforcement tool.
- [ ] Confirm whether Spring Modulith should be adopted in V0.1 or deferred.
- [ ] Confirm whether modules should each own their Flyway migrations or share a single migration sequence.
