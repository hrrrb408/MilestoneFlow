# ADR-BE-009: API Response Envelope

| Field | Value |
|-------|-------|
| Status | **Proposed** |
| Date | 2026-06-07 |
| Deciders | Backend team, Architect |

## Background

The architecture doc §9 defines a unified response envelope: `{ "data": ..., "meta": { "requestId": "..." } }`. All API endpoints must use this envelope consistently — the doc explicitly states "禁止部分端点包 data、部分端点不包".

The B0 baseline already implements `ApiResponse<T>` and `ApiErrorResponse` as Java records.

## Constraints

1. Success responses: `{ "data": { ... }, "meta": { "requestId": "..." } }`.
2. Error responses: `{ "timestamp", "status", "code", "message", "requestId", "path", "fieldErrors", "details" }`.
3. All endpoints use the same envelope — no exceptions.
4. `201 Created` responses include `Location` header and response body.
5. `204 No Content` only for truly empty responses (rare).

## Options

### Option A: Return `ResponseEntity<ApiResponse<T>>` from every controller

```java
@PostMapping
public ResponseEntity<ApiResponse<ProjectDto>> create(@RequestBody CreateProjectRequest req) {
    ProjectDto dto = service.create(req);
    return ResponseEntity.status(201)
            .header("Location", "/api/v1/projects/" + dto.id())
            .body(ApiResponse.of(dto, requestId));
}
```

### Option B: Use `ResponseBodyAdvice` to auto-wrap

```java
@ControllerAdvice
public class EnvelopeAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public Object beforeBodyWrite(Object body, ...) {
        if (body instanceof ApiResponse) return body;
        return ApiResponse.of(body, requestId);
    }
}
```

### Option C: Mix — `ResponseEntity` for special cases, auto-wrap for defaults

## Recommendation

**Option A: Explicit `ResponseEntity<ApiResponse<T>>`.**

Rationale: Explicit wrapping makes the response contract visible in every controller method. Auto-wrapping via `ResponseBodyAdvice` can cause subtle bugs (e.g., `String` responses handled differently, error responses getting double-wrapped). Explicit is better than implicit for API contracts.

## Advantages

- Response shape is visible in code review.
- No magic — easy to debug.
- `ResponseEntity` allows setting status code and headers per endpoint.
- TypeScript code generation can rely on consistent typing.

## Disadvantages and Risks

- More boilerplate per controller method.
- Developers must remember to wrap — linting or ArchUnit can enforce.
- `ApiResponse.of(null, requestId)` for 204 cases needs a convention.

## Impact on Tests

- Every controller test verifies the `data` / `meta` envelope structure.
- Error response tests verify the `ApiErrorResponse` structure.
- Request ID propagation tested end-to-end.

## Impact on Database

- No direct database impact.

## Items for Architecture Window Confirmation

- [ ] Confirm whether `204 No Content` should ever return an envelope body.
- [ ] Confirm whether pagination responses should nest `items` inside `data` or use a separate `page` structure.
- [ ] Confirm whether `ResponseEntity<ApiResponse<T>>` is acceptable vs auto-wrap.
