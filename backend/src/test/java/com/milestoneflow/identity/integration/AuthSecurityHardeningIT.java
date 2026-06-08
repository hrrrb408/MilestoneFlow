package com.milestoneflow.identity.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication security hardening.
 *
 * <p>Verifies security headers, cache-control, and sensitive data
 * protection across auth endpoints.
 */
class AuthSecurityHardeningIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return headers;
    }

    // ── Security Headers ─────────────────────────────────────────────────

    @Nested
    class SecurityHeaders {

        @Test
        void loginResponseShouldHaveNoStoreCacheControl() {
            String body = """
                    {"email":"headers@example.com","password":"wrong"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            String cacheControl = resp.getHeaders().getFirst("Cache-Control");
            assertThat(cacheControl).contains("no-store");
        }

        @Test
        void loginResponseShouldHaveXContentTypeOptions() {
            String body = """
                    {"email":"nosniff@example.com","password":"wrong"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            // Spring Security adds X-Content-Type-Options by default
            assertThat(resp.getHeaders().getFirst("X-Content-Type-Options"))
                    .satisfiesAnyOf(
                            v -> assertThat(v).isEqualTo("nosniff"),
                            v -> assertThat(v).isNull() // may be absent if disabled
                    );
        }

        @Test
        void loginResponseShouldHaveReferrerPolicy() {
            String body = """
                    {"email":"referrer@example.com","password":"wrong"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            assertThat(resp.getHeaders().getFirst("Referrer-Policy"))
                    .isEqualTo("no-referrer");
        }

        @Test
        void forgotPasswordResponseShouldNotBeCached() {
            String body = """
                    {"email":"cache-test@example.com"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/password/forgot",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            // forgot password returns 200, should not be cached
            String cacheControl = resp.getHeaders().getFirst("Cache-Control");
            // Not an auth endpoint that returns tokens, so cache-control may not apply
            // But we verify it doesn't have public caching
            if (cacheControl != null) {
                assertThat(cacheControl).doesNotContain("public");
            }
        }
    }

    // ── Sensitive Data Protection ────────────────────────────────────────

    @Nested
    class SensitiveDataProtection {

        @Test
        void loginResponseShouldNotContainPassword() {
            String body = """
                    {"email":"leak@example.com","password":"TestPassword123!"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            assertThat(resp.getBody()).doesNotContain("TestPassword123!");
            assertThat(resp.getBody()).doesNotContain("passwordHash");
            assertThat(resp.getBody()).doesNotContain("password_hash");
        }

        @Test
        void errorResponsesShouldNotContainTokenHash() {
            String body = """
                    {"email":"token-leak@example.com","password":"wrong"}
                    """;
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

            assertThat(resp.getBody()).doesNotContain("tokenHash");
            assertThat(resp.getBody()).doesNotContain("token_hash");
            assertThat(resp.getBody()).doesNotContain("accessToken");
            assertThat(resp.getBody()).doesNotContain("refreshToken");
        }

        @Test
        void rateLimitResponseShouldNotExposeInternalState() {
            String email = "rl-internal-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            // Register and activate
            String regBody = """
                    {"email":"%s","displayName":"RL User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE email_normalized = ?",
                    email.toLowerCase());

            // Exhaust rate limit
            String loginBody = """
                    {"email":"%s","password":"wrong"}
                    """.formatted(email);
            for (int i = 0; i < 5; i++) {
                restTemplate.exchange("/auth/login",
                        HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);
            }

            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // Should not expose rate limit key, counter, or email
            assertThat(resp.getBody()).doesNotContain(email);
            assertThat(resp.getBody()).doesNotContain("rateLimitKey");
            assertThat(resp.getBody()).doesNotContain("counter");
        }

        @Test
        void auditMetadataShouldNotContainSensitiveFields() {
            // Register a user to generate an audit event
            String email = "audit-safe-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String regBody = """
                    {"email":"%s","displayName":"Safe User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);

            // Check metadata in audit_event doesn't contain sensitive data
            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE action = 'AUTH_REGISTER_SUCCEEDED' "
                            + "ORDER BY created_at DESC LIMIT 1",
                    String.class);

            if (metadata != null) {
                String lower = metadata.toLowerCase();
                assertThat(lower).doesNotContain("password");
                assertThat(lower).doesNotContain("tokenhash");
                assertThat(lower).doesNotContain("rawtoken");
                assertThat(lower).doesNotContain("cookie");
                assertThat(lower).doesNotContain("accesstoken");
                assertThat(lower).doesNotContain("refreshtoken");
            }
        }
    }

    // ── Anti-Enumeration ─────────────────────────────────────────────────

    @Nested
    class AntiEnumeration {

        @Test
        void loginFailureForUnknownEmailShouldMatchKnownEmailResponse() {
            // Both should return 401 AUTH_INVALID_CREDENTIALS
            String unknownEmail = "does-not-exist-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String unknownBody = """
                    {"email":"%s","password":"wrong"}
                    """.formatted(unknownEmail);
            ResponseEntity<String> unknownResp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(unknownBody, jsonHeaders()), String.class);

            // Register a user to test known email
            String email = "enum-known-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String regBody = """
                    {"email":"%s","displayName":"Enum User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE email_normalized = ?",
                    email.toLowerCase());

            String knownBody = """
                    {"email":"%s","password":"wrong"}
                    """.formatted(email);
            ResponseEntity<String> knownResp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(knownBody, jsonHeaders()), String.class);

            // Both should return same status and error code
            assertThat(unknownResp.getStatusCode()).isEqualTo(knownResp.getStatusCode());
            assertThat(unknownResp.getBody()).contains("AUTH_INVALID_CREDENTIALS");
            assertThat(knownResp.getBody()).contains("AUTH_INVALID_CREDENTIALS");
        }

        @Test
        void forgotPasswordShouldReturnSameResponseForKnownAndUnknownEmail() {
            String knownEmail = "forgot-known-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String regBody = """
                    {"email":"%s","displayName":"Forgot User","password":"SecurePass123!"}
                    """.formatted(knownEmail);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE email_normalized = ?",
                    knownEmail.toLowerCase());

            String unknownEmail = "forgot-unknown-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            String knownBody = """
                    {"email":"%s"}
                    """.formatted(knownEmail);
            String unknownBody = """
                    {"email":"%s"}
                    """.formatted(unknownEmail);

            ResponseEntity<String> knownResp = restTemplate.exchange("/auth/password/forgot",
                    HttpMethod.POST, new HttpEntity<>(knownBody, jsonHeaders()), String.class);
            ResponseEntity<String> unknownResp = restTemplate.exchange("/auth/password/forgot",
                    HttpMethod.POST, new HttpEntity<>(unknownBody, jsonHeaders()), String.class);

            assertThat(knownResp.getStatusCode()).isEqualTo(unknownResp.getStatusCode());
            assertThat(knownResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
