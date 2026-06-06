# ADR-BE-003: Money and Amount Mapping

| Field | Value |
|-------|-------|
| Status | **Proposed** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |

## Background

MilestoneFlow handles project quotations, payment schedules, and receivable tracking. All monetary amounts must be precise — no floating-point errors are acceptable. The architecture doc §06 specifies `BigDecimal` in Java, `numeric(19,4)` in PostgreSQL, and decimal strings in JSON (e.g. `"1250.00"`).

## Constraints

1. Java: `BigDecimal` only. Never `double`, `float`, or `Long` cents.
2. Database: `numeric(19,4)` — 15 integer digits + 4 decimal places.
3. JSON: decimal string (`"1250.00"`), never scientific notation or float.
4. Currency is stored as a separate field (ISO 4217, e.g. `"TWD"`, `"USD"`).
5. All amounts must be non-negative unless explicitly allowed (e.g. credit notes).
6. Cross-currency operations are out of scope for V0.1.

## Options

### Option A: Raw BigDecimal fields

```java
@Column(name = "amount", precision = 19, scale = 4)
private BigDecimal amount;

@Column(name = "currency", length = 3)
private String currency;
```

### Option B: JPA `@Embeddable` Money value object

```java
@Embeddable
public record Money(
    @Column(name = "amount", precision = 19, scale = 4) BigDecimal amount,
    @Column(name = "currency", length = 3) String currency
) {}

// Usage
@Embedded
private Money unitPrice;
```

### Option C: Separate `amount` and `currency` columns with a domain Money wrapper

A domain-level value object that wraps `BigDecimal` + `String currency`, persisted as two columns via `@Embeddable`.

## Recommendation

**Option B: JPA `@Embeddable` Money value object.**

Rationale: Embedding `amount` + `currency` into a single value object prevents accidental mixing of currencies (you always handle them as a pair). The `@Embeddable` approach keeps the database schema flat (no join tables) while providing type safety in Java.

## Advantages

- Type-safe: money is always `amount + currency`, never bare `BigDecimal`.
- Flat database schema: `@Embeddable` generates inline columns.
- JSON serialization: custom Jackson serializer writes `{"amount": "1250.00", "currency": "TWD"}`.
- Domain validation (non-negative, currency length) is centralized.

## Disadvantages and Risks

- `@Embeddable` with `record` may require a custom `AttributeConverter` or `@EmbeddableInstantiator` in Hibernate 6+.
- Multiple money fields in one table need `@AttributeOverrides` for column naming.
- Currency conversion is not supported (acceptable for V0.1).

## Impact on Tests

- Money value object tests: equality, validation, comparison.
- JSON serialization test: verify decimal string format (no scientific notation).
- JPA mapping test: verify `numeric(19,4)` storage and retrieval.

## Impact on Database

- Each money field generates two columns: `amount numeric(19,4) NOT NULL` and `currency varchar(3) NOT NULL`.
- Multiple money fields per table: e.g. `unit_price_amount`, `unit_price_currency`.

## Items for Architecture Window Confirmation

- [ ] Confirm `@Embeddable` record approach is acceptable vs raw BigDecimal fields.
- [ ] Confirm whether a shared currency at the workspace level is desired for V0.1.
- [ ] Confirm rounding mode for monetary calculations (e.g. `HALF_UP`).
- [ ] Confirm whether `numeric(19,4)` is sufficient or if `numeric(18,2)` is preferred.
