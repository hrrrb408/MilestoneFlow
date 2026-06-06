# Git Convention

## Branch Naming

| Branch type | Format | Example |
|-------------|--------|---------|
| Feature | `feat/<short-description>` | `feat/api-response-envelope` |
| Bug fix | `fix/<short-description>` | `fix/request-id-mdc-leak` |
| Chore | `chore/<short-description>` | `chore/update-dependencies` |
| CI | `ci/<short-description>` | `ci/backend-workflow` |
| Docs | `docs/<short-description>` | `docs/development-guide` |

## Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>
```

### Types

| Type | Usage |
|------|-------|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `refactor` | Code restructuring without behavior change |
| `test` | Adding or updating tests |
| `chore` | Build, config, dependencies, tooling |
| `ci` | CI/CD configuration |
| `docs` | Documentation only |
| `style` | Formatting, whitespace (no logic change) |

### Scopes

| Scope | Area |
|-------|------|
| `backend` | Java/Spring Boot backend |
| `frontend` | Vue.js frontend |
| `db` | Database migrations |
| `api` | API design, response format |
| `auth` | Authentication/authorization |
| `infra` | Infrastructure, deployment |

### Examples

```
feat(api): add unified response envelope

Implement ApiResponse<T> and ApiErrorResponse matching
architecture spec §9 and §10 for consistent API responses.
```

```
fix(request-id): clean up MDC in finally block

RequestIdFilter now guarantees MDC cleanup even when the
filter chain throws an exception.
```

## Pull Request Process

1. **Create a branch** from `develop` (or `main` if no `develop` exists).
2. **Make focused commits** — each commit should be a logical unit.
3. **Ensure CI passes** — `./mvnw clean verify` must succeed.
4. **Open a PR** against `develop` (or `main`).
5. **Request review** from at least one team member.
6. **Squash-merge** or rebase-merge based on commit quality.

## What NOT to Commit

- `.env` files with real credentials
- IDE configuration (`.idea/`, `.vscode/`)
- Build artifacts (`target/`, `*.class`)
- Database dumps with production data
- Large binary files (use object storage instead)
