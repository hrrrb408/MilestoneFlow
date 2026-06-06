# MilestoneFlow Backend

Backend service for MilestoneFlow — a SaaS platform that helps freelancers and small studios manage project delivery and payment collection.

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 (LTS) |
| Spring Boot | 3.5.x |
| PostgreSQL | 17 |
| Flyway | (managed by Spring Boot) |
| Build Tool | Maven 3.9.x (wrapper) |

## Prerequisites

- **JDK 21** — [Temurin](https://adoptium.net/) or [OpenJDK](https://openjdk.org/)
- **Docker** — for Testcontainers integration tests
- **PostgreSQL 17** — for local development (or use Docker)

## Quick Start

```bash
# 1. Clone and enter the backend directory
cd backend

# 2. Copy environment template
cp .env.example .env
# Edit .env with your local database credentials

# 3. Build and run tests
./mvnw clean verify

# 4. Run the application (requires local PostgreSQL)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The application starts at `http://localhost:8080/api/v1`.

## Available Profiles

| Profile | Purpose | Database |
|---------|---------|----------|
| `local` | Local development | PostgreSQL on `localhost:5432` |
| `test` | Automated tests | Testcontainers PostgreSQL 17 |
| `prod` | Production | Configured via environment variables |

## Environment Variables

See `.env.example` for the full list. Key variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `MF_DATASOURCE_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/milestoneflow` |
| `MF_DATASOURCE_USERNAME` | Database username | `milestoneflow` |
| `MF_DATASOURCE_PASSWORD` | Database password | — |

## Project Structure

```
src/main/java/com/milestoneflow/
├── MilestoneFlowApplication.java   # Spring Boot entry point
└── shared/
    ├── api/                        # Common response types
    │   ├── ApiResponse.java
    │   ├── ApiErrorResponse.java
    │   └── ApiErrorDetail.java
    └── web/                        # Cross-cutting web concerns
        ├── RequestIdFilter.java
        └── GlobalExceptionHandler.java
```

## Useful Commands

```bash
./mvnw clean verify          # Compile + unit tests + integration tests + package
./mvnw test                  # Unit tests only
./mvnw verify -DskipTests    # Compile + package (skip tests)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # Run locally
```

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Documentation

- [Development Guide](docs/development.md)
- [Code Style](docs/code-style.md)
- [Git Convention](docs/git-convention.md)
