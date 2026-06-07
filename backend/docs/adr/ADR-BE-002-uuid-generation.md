# ADR-BE-002: UUID Generation Strategy

| Field | Value |
|-------|-------|
| Status | **Accepted** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |
| Decision Date | 2026-06-07 |
| Decision Makers | System Architect |
| Review Reference | ADR_REVIEW_REPORT.md §ADR-BE-002 |
| Note | UUID v7 approach accepted. Specific library choice deferred — must resolve before MF-BE-006. |

## Background

All primary keys in MilestoneFlow are UUIDs (architecture doc §06). The database model (§01) specifies `id uuid` for all tables. The generation strategy affects ID ordering, storage efficiency, index fragmentation, and debuggability.

## Constraints

1. All business table primary keys are UUID (not auto-increment integers).
2. IDs must not expose creation order or node identity to external clients.
3. IDs must be globally unique across workspaces and environments.
4. Database column type: `uuid` (PostgreSQL native), not `varchar`.
5. Public links use high-entropy tokens, not entity IDs.

## Options

### Option A: UUID v4 (random)

```java
@Id @GeneratedValue(strategy = GenerationType.AUTO)
private UUID id;
```

Hibernate generates UUID v4 by default with `GenerationType.AUTO` on UUID fields.

### Option B: UUID v7 (time-ordered)

```java
@Id
private UUID id = UUIDv7.generate();
```

UUID v7 combines Unix timestamp with random bits, producing monotonically increasing IDs.

### Option C: Client-side generation in application code

```java
@Id
private UUID id = UUID.randomUUID(); // or UUIDv7
```

Entity constructor generates the ID before persistence.

## Recommendation

**Option C with UUID v7** — generate IDs in application code using UUID v7.

Rationale: Client-side generation avoids the round-trip issue where JPA needs to flush to get a server-generated ID. UUID v7 provides time-ordered IDs that reduce B-tree index fragmentation compared to random UUID v4, which is significant at scale. Since the architecture already specifies UUID and PostgreSQL `uuid` column type, this is fully compatible.

## Advantages

- UUID v7 is time-ordered → less B-tree page splits → better INSERT performance.
- IDs available immediately in entity constructors (no flush needed).
- Compatible with PostgreSQL native `uuid` column type.
- Globally unique without coordination.

## Disadvantages and Risks

- UUID v7 is not yet in Hibernate's default generator. Requires a small custom utility.
- UUID v7 reveals creation timestamp (millisecond precision). If this is sensitive, consider v4 instead.
- Slightly larger than `bigserial` (16 bytes vs 8 bytes per row).

## Impact on Tests

- Test fixtures can create entities with known IDs without database round-trip.
- ID equality assertions are deterministic.

## Impact on Database

- All PK columns: `id uuid PRIMARY KEY`.
- Composite FK columns include `workspace_id uuid NOT NULL`.
- Index efficiency: UUID v7 sequential prefix reduces fragmentation vs UUID v4 random.

## Items for Architecture Window Confirmation

- [x] Confirm UUID v7 is acceptable (vs UUID v4 or ULID). — **Accepted.**
- [x] Confirm whether the timestamp leakage in UUID v7 is a security concern. — **Acceptable.** Timestamp leakage is not a concern for primary keys. Public links use separate high-entropy tokens.
- [x] Confirm the UUID v7 library to use (e.g. `java-uuid-generator` or custom implementation). — **`com.fasterxml.uuid:java-uuid-generator:5.2.0`** selected.

## Implementation Record

### Library Selection

| Attribute | Value |
|-----------|-------|
| Library | `com.fasterxml.uuid:java-uuid-generator` |
| Version | 5.2.0 |
| License | Apache 2.0 |
| Maven coordinates | `com.fasterxml.uuid:java-uuid-generator:5.2.0` |
| Java 21 compatible | Yes |
| RFC 9562 UUID v7 | Yes |
| Maintainer | Toshiaki Maki (cowtowncoder) — Jackson/fasterxml ecosystem |
| Transitive dependencies | None |

### Adapter Isolation

- Interface: `com.milestoneflow.shared.id.IdGenerator` — no Spring or library dependency.
- Implementation: `com.milestoneflow.shared.id.UuidV7IdGenerator` — `@Component`, uses `Generators.timeBasedEpochGenerator()`.
- Third-party types (`TimeBasedEpochGenerator`) only appear inside the adapter.
- Business code depends on `IdGenerator` interface only.

### Replacement Cost

Low. Replace `UuidV7IdGenerator` with a new adapter implementing `IdGenerator`. No other code changes required.

### Known Limitations

- UUID v7 timestamp reveals creation millisecond. Not used as business time.
- Monotonic ordering is per-generator within the same millisecond. Not guaranteed across JVM instances.
- `created_at` (JPA auditing via Clock bean) is the authoritative business timestamp.

### Implementation Status

Implemented in MF-BE-004. OPEN-Q-001 resolved.
