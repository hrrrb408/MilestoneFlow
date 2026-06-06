# Development Guide

## Environment Setup

### 1. Install JDK 21

**macOS (Homebrew):**
```bash
brew install openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

**Verify:**
```bash
java -version  # should show 21.x
```

### 2. PostgreSQL 17

**Option A — Homebrew:**
```bash
brew install postgresql@17
brew services start postgresql@17
createdb milestoneflow
```

**Option B — Docker:**
```bash
docker run -d \
  --name milestoneflow-postgres \
  -e POSTGRES_DB=milestoneflow \
  -e POSTGRES_USER=milestoneflow \
  -e POSTGRES_PASSWORD=milestoneflow \
  -p 5432:5432 \
  postgres:17
```

### 3. Configure Environment

```bash
cd backend
cp .env.example .env
# Edit .env with your credentials
```

## Database Migrations

MilestoneFlow uses **Flyway** for database migrations.

### Migration File Naming

```
V001__bootstrap_extensions.sql
V002__create_workspace.sql
V003__create_project.sql
```

- Version number: `V` + zero-padded 3-digit number (V001, V002, ...)
- Separator: double underscore `__`
- Description: lowercase snake_case
- Location: `src/main/resources/db/migration/`

### Guidelines

1. Each migration should focus on **one reviewable topic**.
2. Migrations are **immutable** after creation — never edit an existing migration.
3. Use **Expand-Contract** pattern for schema changes:
   - Expand: add new column/table
   - Migrate application code
   - Contract: remove old column/table in a later migration
4. All business tables must include `workspace_id uuid NOT NULL` for tenant isolation.
5. Use composite foreign keys: `FOREIGN KEY (workspace_id, parent_id) REFERENCES parent(workspace_id, id)`.

## Running Tests

### Unit Tests

```bash
./mvnw test
```

### Integration Tests (requires Docker)

Integration tests use **Testcontainers** to spin up a real PostgreSQL 17 container:

```bash
./mvnw verify
```

The `verify` phase runs both unit tests (`*Test.java` via Surefire) and integration tests (`*IT.java` via Failsafe).

### Running a Single Test

```bash
./mvnw test -Dtest=ApiResponseTest
./mvnw verify -Dit.test=ApplicationStartupIT
```

## Debugging

### Enable Debug Logging

Set the `local` profile and adjust log levels in `application-local.yml`:

```yaml
logging:
  level:
    com.milestoneflow: DEBUG
    org.hibernate.SQL: DEBUG
```

### Remote Debug

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

Then attach your IDE debugger to port 5005.

## API Conventions

- **Base path**: `/api/v1`
- **Content type**: `application/json`
- **Request ID**: Send `X-Request-Id` header (UUID); server generates one if missing.
- **Response envelope**: `{ "data": ..., "meta": { "requestId": "..." } }`
- **Error format**: See architecture doc §10 for the full error response schema.
