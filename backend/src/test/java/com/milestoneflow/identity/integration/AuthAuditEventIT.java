package com.milestoneflow.identity.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication audit event writing.
 *
 * <p>Verifies that auth operations write correct audit_event rows
 * to PostgreSQL with proper fields and sanitized metadata.
 */
class AuthAuditEventIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private int countAuditEvents(String action) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = ?",
                Integer.class, action);
    }

    private Map<String, Object> getLatestAuditEvent(String action) {
        return jdbc.queryForMap(
                "SELECT * FROM audit_event WHERE action = ? ORDER BY created_at DESC LIMIT 1",
                action);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return headers;
    }

    private ResponseEntity<String> registerUser(String email, String displayName) {
        String body = """
                {"email":"%s","displayName":"%s","password":"SecurePass123!"}
                """.formatted(email, displayName);
        return restTemplate.exchange("/auth/register", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> registerAndVerify(String email) {
        String body = """
                {"email":"%s","displayName":"Test User","password":"SecurePass123!"}
                """.formatted(email);
        ResponseEntity<String> regResp = restTemplate.exchange("/auth/register",
                HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Activate user directly via JDBC for test setup
        jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE email_normalized = ?",
                email.toLowerCase());

        return regResp;
    }

    // ── Audit Event Writing ──────────────────────────────────────────────

    @Nested
    class RegistrationAudit {

        @Test
        void shouldWriteAuditOnSuccessfulRegistration() {
            String email = "audit-reg-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerUser(email, "Audit User");

            assertThat(countAuditEvents("AUTH_REGISTER_SUCCEEDED")).isGreaterThan(0);

            Map<String, Object> event = getLatestAuditEvent("AUTH_REGISTER_SUCCEEDED");
            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("actor_id")).isNotNull();
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("app_user");
            assertThat(event.get("request_id")).isNotNull();
            assertThat(event.get("summary")).isNotNull();
        }
    }

    @Nested
    class LoginAudit {

        @Test
        void shouldWriteAuditOnLoginSuccess() {
            String email = "audit-login-ok-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerAndVerify(email);

            String body = """
                    {"email":"%s","password":"SecurePass123!"}
                    """.formatted(email);
            ResponseEntity<String> resp = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(countAuditEvents("AUTH_LOGIN_SUCCEEDED")).isGreaterThan(0);

            Map<String, Object> event = getLatestAuditEvent("AUTH_LOGIN_SUCCEEDED");
            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
        }

        @Test
        void shouldWriteAuditOnLoginFailure() {
            String body = """
                    {"email":"nonexistent@example.com","password":"wrong"}
                    """;
            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), String.class);

            assertThat(countAuditEvents("AUTH_LOGIN_FAILED")).isGreaterThan(0);
        }
    }

    @Nested
    class PasswordAudit {

        @Test
        void shouldWriteAuditOnForgotPasswordRequest() {
            String email = "audit-forgot-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerAndVerify(email);

            String body = """
                    {"email":"%s"}
                    """.formatted(email);
            restTemplate.exchange("/auth/password/forgot", HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), String.class);

            assertThat(countAuditEvents("AUTH_PASSWORD_RESET_REQUESTED")).isGreaterThan(0);
        }
    }

    @Nested
    class MetadataSafety {

        @Test
        void auditMetadataShouldNotContainSensitiveKeys() {
            String email = "audit-safe-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerUser(email, "Safe User");

            Map<String, Object> event = getLatestAuditEvent("AUTH_REGISTER_SUCCEEDED");
            Object metadata = event.get("metadata");

            if (metadata != null) {
                String metadataStr = metadata.toString().toLowerCase();
                assertThat(metadataStr).doesNotContain("password");
                assertThat(metadataStr).doesNotContain("rawtoken");
                assertThat(metadataStr).doesNotContain("tokenhash");
                assertThat(metadataStr).doesNotContain("cookie");
                assertThat(metadataStr).doesNotContain("authorization");
            }
        }
    }

    @Nested
    class AppendOnly {

        @Test
        void shouldRejectAuditEventUpdate() {
            // Insert an audit event first
            String email = "audit-append-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerUser(email, "Append User");

            Map<String, Object> event = getLatestAuditEvent("AUTH_REGISTER_SUCCEEDED");
            UUID eventId = (UUID) event.get("id");

            // Attempt to update should fail
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    jdbc.update("UPDATE audit_event SET summary = 'tampered' WHERE id = ?", eventId)
            ).isInstanceOf(Exception.class);
        }

        @Test
        void shouldRejectAuditEventDelete() {
            String email = "audit-del-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            registerUser(email, "Del User");

            Map<String, Object> event = getLatestAuditEvent("AUTH_REGISTER_SUCCEEDED");
            UUID eventId = (UUID) event.get("id");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    jdbc.update("DELETE FROM audit_event WHERE id = ?", eventId)
            ).isInstanceOf(Exception.class);
        }
    }
}
