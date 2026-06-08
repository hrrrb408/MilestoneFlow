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
 * Integration tests for authentication rate limiting.
 *
 * <p>Verifies that rate limiting works end-to-end with the in-memory
 * rate limiter and returns proper 429 responses.
 */
class AuthRateLimitIT extends AbstractIntegrationTest {

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

    private void activateUser(String email) {
        jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE email_normalized = ?",
                email.toLowerCase());
    }

    // ── Login Rate Limit ─────────────────────────────────────────────────

    @Nested
    class LoginRateLimit {

        @Test
        void shouldReturn429AfterExceedingLoginFailureLimit() {
            // Rate limit config: LOGIN maxAttempts=5 window=15m
            // We need to trigger 6 login failures for the same email
            String email = "rl-login-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            // Register and activate
            String regBody = """
                    {"email":"%s","displayName":"RL User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            activateUser(email);

            // Send 5 failing login attempts
            String loginBody = """
                    {"email":"%s","password":"wrong-password"}
                    """.formatted(email);
            for (int i = 0; i < 5; i++) {
                ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                        HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);
                assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }

            // 6th attempt should be rate limited
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(resp.getBody()).contains("AUTH_RATE_LIMITED");
        }

        @Test
        void shouldReturn429ResponseEnvelope() {
            String email = "rl-envelope-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            String regBody = """
                    {"email":"%s","displayName":"RL User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            activateUser(email);

            String loginBody = """
                    {"email":"%s","password":"wrong-password"}
                    """.formatted(email);
            for (int i = 0; i < 5; i++) {
                restTemplate.exchange("/auth/login",
                        HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);
            }

            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // Response should not contain raw email, token, or internal state
            assertThat(resp.getBody()).doesNotContain(email);
            assertThat(resp.getBody()).doesNotContain("rawToken");
            assertThat(resp.getBody()).doesNotContain("tokenHash");
        }
    }

    // ── Forgot Password Rate Limit ───────────────────────────────────────

    @Nested
    class ForgotPasswordRateLimit {

        @Test
        void shouldMaintainAntiEnumerationWhenRateLimited() {
            // Even when rate limited, forgot password should return same response
            String email = "rl-forgot-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String unknownEmail = "rl-unknown-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            // Exceed rate limit for the known email
            String body = """
                    {"email":"%s"}
                    """.formatted(email);
            for (int i = 0; i < 4; i++) {
                ResponseEntity<String> resp = restTemplate.exchange("/auth/password/forgot",
                        HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
                assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            }

            // Both known and unknown emails should return 200 (anti-enumeration)
            // Note: rate limiting for forgot password uses silent return, so it still returns 200
            ResponseEntity<String> knownResp = restTemplate.exchange("/auth/password/forgot",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
            assertThat(knownResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            String unknownBody = """
                    {"email":"%s"}
                    """.formatted(unknownEmail);
            ResponseEntity<String> unknownResp = restTemplate.exchange("/auth/password/forgot",
                    HttpMethod.POST, new HttpEntity<>(unknownBody, jsonHeaders()), String.class);
            assertThat(unknownResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── Rate Limit Audit ─────────────────────────────────────────────────

    @Nested
    class RateLimitAudit {

        @Test
        void shouldWriteAuditEventOnRateLimitRejection() {
            String email = "rl-audit-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

            String regBody = """
                    {"email":"%s","displayName":"RL Audit User","password":"SecurePass123!"}
                    """.formatted(email);
            restTemplate.exchange("/auth/register", HttpMethod.POST,
                    new HttpEntity<>(regBody, jsonHeaders()), String.class);
            activateUser(email);

            String loginBody = """
                    {"email":"%s","password":"wrong-password"}
                    """.formatted(email);
            for (int i = 0; i < 6; i++) {
                restTemplate.exchange("/auth/login",
                        HttpMethod.POST, new HttpEntity<>(loginBody, jsonHeaders()), String.class);
            }

            // Verify rate limit audit event was written
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE action = 'AUTH_RATE_LIMIT_REJECTED'",
                    Integer.class);
            assertThat(count).isGreaterThan(0);
        }
    }
}
