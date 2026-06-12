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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying project archive/restore audit events.
 */
@DisplayName("Project Archive Audit IT")
class ProjectArchiveAuditIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "archive-audit-it@example.com";
    private static final String WS_SLUG = "archive-audit-ws";

    private String userId;
    private String workspaceId;
    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode("test-password-123");
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Archive Audit IT User", encodedPassword);

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
        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");

        authHeaders = buildAuthHeaders(accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Audit Archive WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        authHeaders = buildAuthHeaders(accessToken);
    }

    private void cleanAll() {
        String norm = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE slug = ?", WS_SLUG);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
    }

    private String extractCookie(ResponseEntity<?> response, String prefix) {
        var setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return null;
        return setCookies.stream().filter(c -> c.startsWith(prefix)).findFirst().orElse(null);
    }

    private HttpHeaders buildAuthHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        String xsrfCookie = extractCookieFromResponse(csrfResponse, "XSRF-TOKEN=");
        if (xsrfCookie != null) {
            String csrfToken = xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
            headers.add("Cookie", xsrfCookie.split(";")[0]);
            headers.add("X-XSRF-TOKEN", csrfToken);
        }
        return headers;
    }

    private String extractCookieFromResponse(ResponseEntity<?> response, String prefix) {
        var setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return null;
        return setCookies.stream().filter(c -> c.startsWith(prefix)).findFirst().orElse(null);
    }

    private String createProject() {
        var body = Map.of("name", "Audit Archive Project");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── PROJECT_ARCHIVED ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PROJECT_ARCHIVED audit event")
    class ProjectArchived {

        @Test
        @DisplayName("should write audit event when project is archived")
        void shouldWriteAuditEvent() {
            String projectId = createProject();

            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_ARCHIVED'",
                    Integer.class, userId);
            assertThat(count).isGreaterThan(0);

            Map<String, Object> event = jdbc.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_ARCHIVED' LIMIT 1",
                    userId);

            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("PROJECT");
            assertThat(event.get("target_id")).isNotNull();
            assertThat(event.get("workspace_id")).isNotNull();
            assertThat(event.get("summary")).isNotNull();
        }
    }

    // ── PROJECT_RESTORED ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PROJECT_RESTORED audit event")
    class ProjectRestored {

        @Test
        @DisplayName("should write audit event when project is restored")
        void shouldWriteAuditEvent() {
            String projectId = createProject();

            // Archive first
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Then restore
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/restore",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_RESTORED'",
                    Integer.class, userId);
            assertThat(count).isGreaterThan(0);

            Map<String, Object> event = jdbc.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id = ?::uuid AND action = 'PROJECT_RESTORED' LIMIT 1",
                    userId);

            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("target_type")).isEqualTo("PROJECT");
            assertThat(event.get("target_id")).isNotNull();
            assertThat(event.get("workspace_id")).isNotNull();
        }
    }
}
