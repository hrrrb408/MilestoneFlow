# MF-BE-012 OpenAPI Guide

## Overview

MilestoneFlow Backend API documentation is generated using **Springdoc OpenAPI** (v2.8.8)
with Spring Boot 3.5.3 compatibility.

## Access

### Local / Test

```bash
# Swagger UI (interactive documentation)
open http://localhost:8080/swagger-ui.html

# Raw JSON
curl http://localhost:8080/v3/api-docs
```

### Production

Swagger UI is **disabled** in production by default.
API docs can be enabled via `SPRINGDOC_API_DOCS_ENABLED=true` if needed.

```yaml
# application-prod.yml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: ${SPRINGDOC_API_DOCS_ENABLED:false}
```

## Authentication Scheme

The API uses **HttpOnly cookie-based opaque token authentication** — not JWT Bearer.

| Cookie | Purpose | Attributes |
|--------|---------|------------|
| `MF_ACCESS` | Opaque access token | HttpOnly, SameSite=Lax, Path=/api/v1 |
| `MF_REFRESH` | Opaque refresh token | HttpOnly, SameSite=Strict, Path=/api/v1/auth/refresh |
| `XSRF-TOKEN` | CSRF protection | Non-HttpOnly (read by SPA), SameSite=Lax |

CSRF token must be sent as `X-XSRF-TOKEN` header on state-changing requests.

## Documented Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /auth/register | No | Register new user |
| POST | /auth/email-verification/resend | No | Resend verification email |
| POST | /auth/email-verification/confirm | No | Confirm email verification |
| POST | /auth/login | No | Login |
| POST | /auth/refresh | No* | Refresh access token |
| GET | /auth/me | Yes | Get current user |
| POST | /auth/logout | Yes | Logout current session |
| POST | /auth/logout-all | Yes | Logout all sessions |
| POST | /auth/password/change | Yes | Change password |
| POST | /auth/password/forgot | No | Request password reset |
| POST | /auth/password/reset | No | Reset password |

*Refresh reads MF_REFRESH cookie but is publicly accessible (no authentication header needed).

## Error Response Schema

All errors use the unified `ApiErrorResponse` format:

```json
{
  "timestamp": "2026-06-11T08:00:00Z",
  "status": 401,
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid email or password.",
  "requestId": "req-abc123",
  "path": "/auth/login",
  "fieldErrors": [],
  "details": {}
}
```

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_FAILED` | 422 | Request field validation failed |
| `INVALID_REQUEST` | 400 | Malformed or unreadable request |
| `AUTH_UNAUTHENTICATED` | 401 | Not authenticated |
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | Email not verified |
| `AUTH_ACCOUNT_DISABLED` | 401 | Account is disabled |
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | Email already registered |
| `AUTH_VERIFICATION_TOKEN_INVALID` | 401 | Token not found or used |
| `AUTH_VERIFICATION_TOKEN_EXPIRED` | 401 | Verification token expired |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Refresh token not found |
| `AUTH_REFRESH_TOKEN_EXPIRED` | 401 | Refresh token expired |
| `AUTH_REFRESH_TOKEN_REUSED` | 401 | Refresh token replay detected |
| `AUTH_SESSION_REVOKED` | 401 | Session revoked |
| `AUTH_PASSWORD_POLICY_VIOLATION` | 422 | Password doesn't meet policy |
| `AUTH_PASSWORD_RESET_TOKEN_INVALID` | 401 | Reset token not found or used |
| `AUTH_PASSWORD_RESET_TOKEN_EXPIRED` | 401 | Reset token expired |
| `AUTH_RATE_LIMITED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Exporting OpenAPI JSON

```bash
# Start the application with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Export OpenAPI JSON
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool \
  > backend/docs/openapi/milestoneflow-openapi-v0.1.json
```

Alternatively with Testcontainers:

```bash
./mvnw verify -DskipTests
# Then start and curl as above
```

## Security Notes

- No JWT Bearer scheme appears in documentation
- No real passwords, tokens, or cookie values in examples
- Swagger UI disabled in production by default
- API docs endpoint configurable via `SPRINGDOC_API_DOCS_ENABLED`
