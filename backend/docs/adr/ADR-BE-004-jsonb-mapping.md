# ADR-BE-004: JSONB Column Mapping

| Field | Value |
|-------|-------|
| Status | **Proposed** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |

## Background

Some entities store semi-structured or extensible data that doesn't warrant dedicated columns. Examples include: file metadata, notification payload templates, and AI-generated structured outputs. PostgreSQL supports `jsonb` columns for this purpose.

## Constraints

1. JSONB columns must not be used as a substitute for well-defined relational columns.
2. Data stored in JSONB must not be used for business-critical queries (no WHERE on JSONB paths for core flows).
3. JSONB content should be validated at the application layer before persistence.
4. No business rules depend on JSONB column structure — only presentation and convenience features.

## Options

### Option A: Hibernate 6 native JSONB support

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata", columnDefinition = "jsonb")
private Map<String, Object> metadata;
```

### Option B: Custom `AttributeConverter<Map, String>`

Manually serialize/deserialize JSON with Jackson inside a JPA converter.

### Option C: Avoid JSONB entirely; use separate tables or columns

Store all structured data in relational columns.

## Recommendation

**Option A: Hibernate 6 native JSONB support.**

Rationale: Hibernate 6 (included in Spring Boot 3.5.x) has built-in JSON type support via `@JdbcTypeCode(SqlTypes.JSON)`. This is the simplest approach, requires no custom converter, and integrates with the existing Jackson `ObjectMapper`.

## Advantages

- Zero boilerplate: Hibernate handles serialization/deserialization.
- Works with Jackson's `ObjectMapper` automatically.
- PostgreSQL can validate JSON syntax at insert time.
- Future GIN index support for optional search.

## Disadvantages and Risks

- JSONB queries are harder to optimize than regular column queries.
- Schema changes in JSONB are invisible to Flyway migrations.
- Overuse of JSONB can hide data model issues.

## Impact on Tests

- JSONB round-trip test: insert entity, read back, verify structure.
- Verify that invalid JSON is rejected by PostgreSQL.

## Impact on Database

- Column type: `jsonb` with a CHECK constraint or application-level validation.
- Consider adding a GIN index if queries on JSONB paths become necessary.

## Items for Architecture Window Confirmation

- [ ] Confirm which entities will use JSONB columns (file metadata? notification payloads?).
- [ ] Confirm whether JSONB content validation should be at DB level (CHECK) or application level.
- [ ] Confirm whether GIN indexes are needed for V0.1.
