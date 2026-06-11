package com.milestoneflow.workspace.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying workspace audit events are written.
 */
@DisplayName("Workspace Audit IT")
class WorkspaceAuditIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "ws-audit-it@example.com";

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode("test-password-123");
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Audit IT User", encodedPassword);

        // Login
        var body = Map.of("email", EMAIL, "password", "test-password-123");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        String accessToken = loginResponse.getHeaders().getFirst("Set-Cookie");

        // Get CSRF token
        var csrfHeaders = new HttpHeaders();
        csrfHeaders.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);
        String csrfCookie = csrfResponse.getHeaders().getFirst("Set-Cookie");
        String csrfToken = csrfCookie != null ? csrfCookie.split("XSRF-TOKEN=")[1].split(";")[0] : "";

        // Store auth context for tests
        System.setProperty("test.cookie.access", accessToken != null ? accessToken : "");
        System.setProperty("test.cookie.csrf", csrfCookie != null ? csrfCookie : "");
        System.setProperty("test.csrf.token", csrfToken);
    }

    private void cleanAll() {
        String email = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", email);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", email);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE created_by IN (SELECT id FROM app_user WHERE email_normalized = ?)", email);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", email);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", email);
    }

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", System.getProperty("test.cookie.access"));
        headers.add("Cookie", System.getProperty("test.cookie.csrf"));
        headers.add("X-XSRF-TOKEN", System.getProperty("test.csrf.token"));
        return headers;
    }

    // ── WORKSPACE_CREATED ────────────────────────────────────────────────

    @Nested
    @DisplayName("WORKSPACE_CREATED audit event")
    class WorkspaceCreated {

        @Test
        @DisplayName("should write audit event when workspace is created")
        void shouldWriteAuditEvent() {
            String userId = jdbc.queryForObject(
                    "SELECT id::text FROM app_user WHERE email_normalized = ?",
                    String.class, EMAIL.toLowerCase());

            var body = Map.of("name", "Audit Test", "slug", "audit-test");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            // Verify audit event
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE actor_id = ?::uuid AND action = 'WORKSPACE_CREATED'",
                    Integer.class, userId);
            assertThat(count).isGreaterThan(0);

            Map<String, Object> event = jdbc.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id = ?::uuid AND action = 'WORKSPACE_CREATED' LIMIT 1",
                    userId);

            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("workspace");
            assertThat(event.get("summary")).isNotNull();
        }
    }
}
