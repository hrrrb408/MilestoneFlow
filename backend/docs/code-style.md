# Code Style Guide

## Java Version

- **Source level**: Java 21
- Use modern Java features: records, sealed classes, pattern matching, text blocks.

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, dot-separated | `com.milestoneflow.shared.api` |
| Class | PascalCase | `ApiResponse`, `GlobalExceptionHandler` |
| Method | camelCase | `resolveRequestId()` |
| Variable | camelCase | `fieldErrors` |
| Constant | UPPER_SNAKE_CASE | `HEADER_NAME`, `MDC_KEY` |
| JSON field | camelCase | `requestId`, `fieldErrors` |
| Database table | snake_case | `workspace_membership` |
| Database column | snake_case | `created_at`, `workspace_id` |
| Enum value | UPPER_SNAKE_CASE | `PENDING_ACCEPTANCE` |
| Error code | UPPER_SNAKE_CASE | `VALIDATION_FAILED` |

## Package Structure

Follow the domain-module layout from the architecture doc:

```
com.milestoneflow
├── MilestoneFlowApplication.java
├── sharedkernel/       # Core shared types (not shared/)
├── identity/           # User registration, authentication
├── workspace/          # Workspace configuration
├── customer/           # Client management
├── project/            # Project lifecycle
├── quotation/          # Quote drafts and versions
├── baseline/           # Commercial baseline snapshots
├── delivery/           # Milestones and deliveries
├── changeorder/        # Change requests
├── receivable/         # Receivables and payments
├── publicaccess/       # Public links and sessions
├── fileasset/          # File management
├── notification/       # Email tasks
├── audit/              # Audit events
├── actioncenter/       # Action item projections
├── feedbackview/       # Feedback aggregations
└── scheduler/          # Job leasing and scheduling
```

Each domain module follows this internal structure:

```
module/
├── domain/             # Entities, value objects, domain services
├── application/        # Application services, DTOs
├── api/                # REST controllers
└── infrastructure/     # Repository implementations, adapters
```

## Key Rules

1. **No cross-module JPA Entity or Repository access.** Use Application Service APIs or domain events.
2. **All Repository methods must include `workspaceId` as the first parameter.** No bare `findById()`.
3. **Use `BigDecimal` for monetary amounts**, never `double` or `float`.
4. **Use `UUID` for primary keys**, never auto-increment.
5. **Immutable published versions.** Once a quote/delivery/change is published, the content snapshot must not change.

## Logging

- Use SLF4J (`org.slf4j.Logger`) — never `System.out.println`.
- The `requestId` is automatically set in MDC by `RequestIdFilter`.
- Log format includes `req=%X{requestId}` for correlation.
- Never log passwords, tokens, API keys, or full sensitive documents.

## Exception Handling

- Business exceptions should carry a stable error code (e.g. `PROJECT_ARCHIVED`).
- All exceptions are caught by `GlobalExceptionHandler` and mapped to `ApiErrorResponse`.
- Never expose stack traces, SQL, or class names in production responses.

## Testing

- Unit tests: `*Test.java` — fast, no Spring context, no database.
- Integration tests: `*IT.java` — Spring context + Testcontainers PostgreSQL.
- Test naming: `should[ExpectedBehavior]When[Condition]` or `should[DoSomething]`.
- Use AssertJ for fluent assertions.
- Use Mockito for mocking in unit tests.
