# ADR-BE-010: HTTP Status Code for Validation Errors

| Field | Value |
|-------|-------|
| Status | **Accepted** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |
| Decision Date | 2026-06-07 |
| Decision Makers | System Architect |
| Review Reference | ADR_REVIEW_REPORT.md §ADR-BE-010 |
| Note | Aligned with architecture doc §11. Duplicate email → 409 (unique constraint conflict). One error code maps to one HTTP status. |

## Background

The architecture doc §11 defines HTTP status code usage. Two codes are relevant for validation: `400 Bad Request` for malformed requests (JSON syntax, parameter format) and `422 Unprocessable Entity` for semantically valid but business-rule-invalid requests. The B0 `GlobalExceptionHandler` already implements this split.

## Constraints

1. `400 Bad Request`: JSON syntax errors, missing required fields in the wrong way, parameter type mismatch.
2. `422 Unprocessable Entity`: Business field validation failures (blank required field, invalid email format, amount must be positive).
3. `409 Conflict`: Business state conflicts (already published, version mismatch, idempotency key reused).
4. Error codes in the `code` field (not HTTP status): `INVALID_REQUEST` for 400, `VALIDATION_FAILED` for 422.
5. `fieldErrors` array is only present for 422 responses.

## Options

### Option A: 422 for all validation errors (Bean Validation + manual)

Use `422` for everything that passes JSON parsing but fails validation.

### Option B: Split: 400 for structural errors, 422 for semantic validation (current implementation)

- `400 INVALID_REQUEST`: `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`.
- `422 VALIDATION_FAILED`: `MethodArgumentNotValidException`, `ConstraintViolationException`.

### Option C: 400 for everything (RFC 7231 conservative reading)

Some API guidelines use `400` for all client errors.

## Recommendation

**Option B: Split 400/422 (current B0 implementation).**

Rationale: The architecture doc §11 explicitly lists both 400 and 422 with different meanings. The split helps frontend developers distinguish between "your request was malformed" (400, likely a code bug) and "your data didn't pass validation" (422, show to the user). This is also consistent with RFC 4918 (WebDAV) which defines 422 for semantic errors.

## Advantages

- Matches architecture doc §11 exactly.
- Frontend can distinguish structural vs semantic errors.
- `fieldErrors` array is consistently present for 422, absent for 400.
- Error code (`INVALID_REQUEST` vs `VALIDATION_FAILED`) adds another disambiguation layer.

## Disadvantages and Risks

- Not all HTTP clients handle 422 consistently (some treat it as a generic 4xx).
- Some API guidelines (e.g., Microsoft REST API Guidelines) prefer 400 for all validation.
- Edge cases: is a blank JSON field "structural" or "semantic"? (Recommendation: semantic → 422.)

## Impact on Tests

- `GlobalExceptionHandlerTest` already verifies the 400/422 split.
- Future business controller tests should verify correct status codes per scenario.

## Impact on Database

- No direct database impact.

## Items for Architecture Window Confirmation

- [ ] Confirm the 400/422 split is acceptable (vs 400 for everything).
- [ ] Confirm whether `@Valid` on request body should always produce 422.
- [ ] Confirm whether path parameter type mismatches should produce 400 or 404.
