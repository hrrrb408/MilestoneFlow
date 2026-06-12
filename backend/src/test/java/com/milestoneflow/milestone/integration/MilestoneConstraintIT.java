package com.milestoneflow.milestone.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database constraint integration tests for milestone table.
 *
 * <p>Verifies FK constraints, CHECK constraints, and data integrity
 * at the database level.
 */
@DisplayName("Milestone Constraint IT")
class MilestoneConstraintIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "ms-constraint-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "ms-constraint-ws";

    private UUID workspaceId;
    private UUID projectId;
    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Constraint IT User", encodedPassword);

        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        authHeaders = buildAuthHeaders(accessToken);

        var wsBody = Map.of("name", "Constraint WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = UUID.fromString((String) wsData.get("id"));

        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Constraint Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = UUID.fromString((String) projData.get("id"));
    }

    private void cleanAll() {
        String norm = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM milestone WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
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
        var csrfHeaders = new HttpHeaders();
        csrfHeaders.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);
        var setCookies = csrfResponse.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            String xsrfCookie = setCookies.stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElse(null);
            if (xsrfCookie != null) {
                String csrfToken = xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
                headers.add("X-XSRF-TOKEN", csrfToken);
                headers.add("Cookie", xsrfCookie.split(";")[0]);
            }
        }
        return headers;
    }

    @Test
    @DisplayName("should enforce FK to project table")
    void shouldEnforceProjectFk() {
        UUID fakeProjectId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbc.update("""
                    INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'FK Test', 'OPEN', '{}', 0, now(), now())
                    """, workspaceId, fakeProjectId)
        ).hasMessageContaining("fk_milestone_project");
    }

    @Test
    @DisplayName("should enforce FK to workspace table")
    void shouldEnforceWorkspaceFk() {
        UUID fakeWorkspaceId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbc.update("""
                    INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'FK Test', 'OPEN', '{}', 0, now(), now())
                    """, fakeWorkspaceId, projectId)
        ).hasMessageContaining("fk_milestone_workspace");
    }

    @Test
    @DisplayName("should enforce status CHECK constraint")
    void shouldEnforceStatusCheck() {
        assertThatThrownBy(() ->
                jdbc.update("""
                    INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'Bad Status', 'INVALID', '{}', 0, now(), now())
                    """, workspaceId, projectId)
        ).hasMessageContaining("ck_milestone_status");
    }

    @Test
    @DisplayName("should enforce settings jsonb CHECK constraint")
    void shouldEnforceSettingsCheck() {
        assertThatThrownBy(() ->
                jdbc.update("""
                    INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'Bad Settings', 'OPEN', '"not an object"', 0, now(), now())
                    """, workspaceId, projectId)
        ).hasMessageContaining("ck_milestone_settings");
    }

    @Test
    @DisplayName("should allow OPEN and COMPLETED statuses")
    void shouldAllowValidStatuses() {
        // OPEN
        jdbc.update("""
            INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'Open MS', 'OPEN', '{}', 0, now(), now())
            """, workspaceId, projectId);

        // COMPLETED
        jdbc.update("""
            INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'Completed MS', 'COMPLETED', '{}', 0, now(), now())
            """, workspaceId, projectId);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM milestone WHERE project_id = ?::uuid",
                Integer.class, projectId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should reject archived project milestone creation via API")
    void shouldRejectArchivedProjectCreate() {
        // Archive the project via API
        ResponseEntity<Map> archiveResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Try creating a milestone in archived project
        authHeaders = buildAuthHeaders(extractCookie(
                restTemplate.exchange("/auth/login", HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                        Map.class), "MF_ACCESS="));

        var msBody = Map.of("title", "In Archived Project");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBody, authHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PROJECT_ARCHIVED");
    }
}
