# MF-BE-012 Release Readiness Checklist

## B1 Authentication Backend — Pre-Release Security Review

**Date**: 2026-06-11
**Branch**: `feat/MF-BE-012-email-openapi-release-readiness`
**Stacked on**: `feat/MF-BE-011-auth-audit-rate-limit-hardening-final`

---

## 1. Configuration Check

| Item | Status | Notes |
|------|--------|-------|
| prod profile exists | ✅ | `application-prod.yml` |
| database url from env | ✅ | `${MF_DATASOURCE_URL}` |
| mail provider = smtp | ✅ | `milestoneflow.mail.provider=smtp` in prod |
| SMTP credentials from env | ✅ | `${SMTP_HOST}`, `${SMTP_PORT}`, `${SMTP_USERNAME}`, `${SMTP_PASSWORD}` |
| frontend-base-url HTTPS | ✅ | `${MILESTONEFLOW_FRONTEND_BASE_URL}` — must be HTTPS |
| cookie secure = true | ✅ | `milestoneflow.auth.cookies.secure: true` in prod |
| SameSite access = Lax | ✅ | Default in `AuthCookieProperties` |
| SameSite refresh = Strict | ✅ | Default in `AuthCookieProperties` |
| CSRF enabled | ✅ | `CookieCsrfTokenRepository` in `SecurityConfiguration` |
| Rate limit enabled | ✅ | `milestoneflow.auth.rate-limit.enabled: true` |
| Audit enabled | ✅ | `AuthAuditWriterAdapter` always active |
| Swagger UI prod disabled | ✅ | `springdoc.swagger-ui.enabled: false` in prod |
| Logging level prod = WARN/INFO | ✅ | root=WARN, com.milestoneflow=INFO |
| No secrets in repo | ✅ | `.env.example` has placeholders only |

## 2. API Security Check

| Item | Status | Notes |
|------|--------|-------|
| No JWT | ✅ | Opaque tokens only, HttpOnly cookies |
| HttpOnly cookies | ✅ | MF_ACCESS, MF_REFRESH are HttpOnly |
| Refresh path limited | ✅ | Path=/api/v1/auth/refresh, SameSite=Strict |
| Token hash only in DB | ✅ | `auth_session.access_token_hash`, `refresh_token_hash` |
| Raw token never logged | ✅ | Events redact in `toString()`, senders mask |
| Password never logged | ✅ | Request DTOs redact in `toString()` |
| Audit metadata sanitized | ✅ | `rawToken`, `password` keys rejected |
| Rate limit enabled | ✅ | In-memory per-IP rate limiting |
| Anti-enumeration preserved | ✅ | Forgot password always returns 200 |
| Email sender logs sanitized | ✅ | `maskEmail()`, no raw token in logs |

## 3. Database Check

| Item | Status | Notes |
|------|--------|-------|
| Flyway V001–V007 | ✅ | Unmodified, no new migrations |
| Hibernate validate | ✅ | `ddl-auto: validate` in all profiles |
| audit_event append-only | ✅ | No UPDATE/DELETE on audit_event table |
| No cascade delete | ✅ | FK constraints use RESTRICT |
| No raw token columns | ✅ | Only `token_hash` stored |
| No plaintext password columns | ✅ | Only `password_hash` stored |

## 4. Email Provider Check

| Item | Status | Notes |
|------|--------|-------|
| SMTP VerificationEmailSender | ✅ | `SmtpVerificationEmailSender` with `@Profile("prod")` |
| SMTP PasswordResetEmailSender | ✅ | `SmtpPasswordResetEmailSender` with `@Profile("prod")` |
| local/test Noop not regressed | ✅ | `@Profile({"local","test"})` + `@ConditionalOnProperty` |
| prod does not allow Noop | ✅ | `@Profile("prod")` + `@ConditionalOnProperty(provider=smtp)` |
| prod missing SMTP fails to start | ✅ | Fail-closed: no bean → startup failure |
| Email template correct | ✅ | Plain text with link, TTL, ignore notice |
| Raw token in email body only | ✅ | Not in logs, audit, API responses, DB |
| Email failure sanitized | ✅ | `EmailSendException` wraps cause, no token |
| Events AFTER_COMMIT | ✅ | Both listeners use `TransactionPhase.AFTER_COMMIT` |

## 5. OpenAPI Check

| Item | Status | Notes |
|------|--------|-------|
| OpenAPI dependency correct | ✅ | `springdoc-openapi-starter-webmvc-ui:2.8.8` |
| Auth endpoints documented | ✅ | All 11 endpoints with @Operation/@ApiResponses |
| Request/response schema | ✅ | All DTOs have validation annotations |
| Error schema complete | ✅ | `ApiErrorResponse`, `ApiErrorDetail` schemas |
| Rate limit error documented | ✅ | 429 response in relevant endpoints |
| Cookie auth explained | ✅ | `cookieAuth` scheme with MF_ACCESS |
| No JWT Bearer | ✅ | Verified in OpenApiDocumentationIT |
| Swagger UI prod disabled | ✅ | `springdoc.swagger-ui.enabled: false` |

## 6. Release Blockers

| Blocker | Status |
|---------|--------|
| Real email provider not configured | ✅ Resolved — SMTP senders implemented |
| prod still using noop | ✅ Resolved — `@Profile("prod")` + `provider=smtp` |
| OpenAPI not verified | ✅ Resolved — OpenApiDocumentationIT added |
| Security tests failing | ✅ Not applicable — all 541 unit tests pass |
| CI clean verify failing | ⏳ Pending — local Docker incompatible, CI required |
| Secrets in configuration | ✅ No secrets — all env var placeholders |

## 7. Post-Release Verification

After deployment, verify:
- [ ] Application starts with `prod` profile
- [ ] Registration sends real verification email
- [ ] Forgot password sends real reset email
- [ ] Email links work (correct URL, token accepted)
- [ ] Swagger UI not accessible
- [ ] `/v3/api-docs` returns 404 or disabled
- [ ] Rate limiting active on login/register
- [ ] Audit events recorded for auth operations

---

**Verdict**: All configuration and code-level checks pass. Final release requires CI `./mvnw clean verify` success on GitHub Actions.
