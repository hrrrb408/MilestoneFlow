package com.milestoneflow.project.integration;

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
 * Integration tests verifying project audit events are written.
 */
@DisplayName("Project Audit IT")
class ProjectAuditIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "project-audit-it@example.com";

    private String userId;
    private String workspaceId;
    private HttpHeaders authHeaders;
    private HttpHeaders authHeadersOnly;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode("test-password-123");
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Project Audit IT User", encodedPassword);

        userId = jdbc.queryForObject(
                "SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());

        // Login
        var body = Map.of("email", EMAIL, "password", "test-password-123");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        var setCookies = loginResponse.getHeaders().get("Set-Cookie");
        String accessToken = setCookies.stream()
                .filter(c -> c.startsWith("MF_ACCESS="))
                .findFirst()
                .orElse(null);

        // Get CSRF token
        var csrfHeaders = new HttpHeaders();
        csrfHeaders.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);

        var csrfSetCookies = csrfResponse.getHeaders().get("Set-Cookie");
        String xsrfCookie = csrfSetCookies != null
                ? csrfSetCookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElse(null)
                : null;
        String csrfToken = xsrfCookie != null ? xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0] : "";

        authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.add("Cookie", accessToken);
        if (xsrfCookie != null) {
            authHeaders.add("Cookie", xsrfCookie.split(";")[0]);
        }
        authHeaders.add("X-XSRF-TOKEN", csrfToken);

        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Audit WS", "slug", "audit-ws-proj");
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");
    }

    private void cleanAll() {
        String norm = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE created_by IN (SELECT id FROM app_user WHERE email_normalized = ?))", norm);
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE created_by IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
    }

    // ── PROJECT_CREATED ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PROJECT_CREATED audit event")
    class ProjectCreated {

        @Test
        @DisplayName("should write audit event when project is created")
        void shouldWriteAuditEvent() {
            var body = Map.of("name", "Audit Project", "description", "Test audit");
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            // Verify audit event
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_CREATED'",
                    Integer.class, userId);
            assertThat(count).isGreaterThan(0);

            Map<String, Object> event = jdbc.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_CREATED' LIMIT 1",
                    userId);

            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("PROJECT");
            assertThat(event.get("workspace_id")).isNotNull();
            assertThat(event.get("summary")).isNotNull();
        }
    }

    // ── PROJECT_UPDATED ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PROJECT_UPDATED audit event")
    class ProjectUpdated {

        @Test
        @DisplayName("should write audit event when project is updated")
        void shouldWriteAuditEvent() {
            // Create project
            var createBody = Map.of("name", "Before Update");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                    new HttpEntity<>(createBody, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String projectId = (String) data.get("id");

            // Update project
            var updateBody = Map.of("name", "After Update");
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            // Verify audit event
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_UPDATED'",
                    Integer.class, userId);
            assertThat(count).isGreaterThan(0);

            Map<String, Object> event = jdbc.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_UPDATED' LIMIT 1",
                    userId);

            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("target_type")).isEqualTo("PROJECT");
            assertThat(event.get("target_id")).isNotNull();
        }
    }
}
