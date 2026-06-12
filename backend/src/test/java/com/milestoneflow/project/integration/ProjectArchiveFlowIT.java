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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for project archive and restore flows.
 */
@DisplayName("Project Archive Flow IT")
class ProjectArchiveFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "archive-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "archive-flow-ws";

    private String workspaceId;
    private HttpHeaders authHeaders;
    private HttpHeaders authHeadersOnly;

    @BeforeEach
    void setUp() {
        cleanAll();

        // Create a verified ACTIVE user
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Archive Flow IT User", encodedPassword);

        // Login
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");

        // Build auth headers
        authHeaders = buildAuthHeaders(accessToken);
        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Archive Test WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);

        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Refresh authHeaders after workspace creation
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
                headers.add("Cookie", xsrfCookie.split(";")[0]);
                headers.add("X-XSRF-TOKEN", csrfToken);
            }
        }
        return headers;
    }

    private String projectBasePath() {
        return "/workspaces/" + workspaceId + "/projects";
    }

    private String createProject(String name) {
        var body = Map.of("name", name);
        ResponseEntity<Map> response = restTemplate.exchange(
                projectBasePath(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── POST /projects/{id}/archive ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /workspaces/{id}/projects/{id}/archive")
    class ArchiveProject {

        @Test
        @DisplayName("should archive ACTIVE project")
        void shouldArchiveActiveProject() {
            String projectId = createProject("To Archive");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("ARCHIVED");
            assertThat(data.get("archivedAt")).isNotNull();

            // Verify DB state
            Map<String, Object> db = jdbc.queryForMap(
                    "SELECT status, archived_at, archived_by FROM project WHERE id = ?::uuid", projectId);
            assertThat(db.get("status")).isEqualTo("ARCHIVED");
            assertThat(db.get("archived_at")).isNotNull();
            assertThat(db.get("archived_by")).isNotNull();
        }

        @Test
        @DisplayName("should reject double archive with 409")
        void shouldRejectDoubleArchive() {
            String projectId = createProject("Double Archive");

            // First archive
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Second archive
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_ARCHIVED");
        }

        @Test
        @DisplayName("should return 404 for non-existent project archive")
        void shouldReturn404ForNonExistent() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + UUID.randomUUID() + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── POST /projects/{id}/restore ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /workspaces/{id}/projects/{id}/restore")
    class RestoreProject {

        @Test
        @DisplayName("should restore ARCHIVED project")
        void shouldRestoreArchivedProject() {
            String projectId = createProject("To Restore");

            // Archive first
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Now restore
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/restore", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("ACTIVE");
            assertThat(data.get("archivedAt")).isNull();

            // Verify DB state
            Map<String, Object> db = jdbc.queryForMap(
                    "SELECT status, archived_at, archived_by FROM project WHERE id = ?::uuid", projectId);
            assertThat(db.get("status")).isEqualTo("ACTIVE");
            assertThat(db.get("archived_at")).isNull();
            assertThat(db.get("archived_by")).isNull();
        }

        @Test
        @DisplayName("should reject restore of ACTIVE project with 409")
        void shouldRejectRestoreActive() {
            String projectId = createProject("Active Restore");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/restore", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_NOT_ARCHIVED");
        }

        @Test
        @DisplayName("should return 404 for non-existent project restore")
        void shouldReturn404ForNonExistent() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + UUID.randomUUID() + "/restore", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── List filtering ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects with filters")
    class ListFiltering {

        @Test
        @DisplayName("default list excludes ARCHIVED projects")
        void defaultListExcludesArchived() {
            createProject("Active A");
            String projectId = createProject("To Archive");
            createProject("Active B");

            // Archive one
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.stream().allMatch(i -> "ACTIVE".equals(i.get("status")))).isTrue();
        }

        @Test
        @DisplayName("includeArchived=true returns both ACTIVE and ARCHIVED")
        void includeArchivedTrue() {
            String projectId = createProject("To Archive");
            createProject("Stays Active");

            // Archive one
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "?includeArchived=true", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("status=ARCHIVED returns only archived projects")
        void statusArchived() {
            String projectId = createProject("To Archive");
            createProject("Stays Active");

            // Archive one
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "?status=ARCHIVED", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("ARCHIVED");
            assertThat(items.get(0).get("archivedAt")).isNotNull();
        }

        @Test
        @DisplayName("status=ACTIVE returns only active projects")
        void statusActive() {
            String projectId = createProject("To Archive");
            createProject("Stays Active");

            // Archive one
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "?status=ACTIVE", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("ACTIVE");
        }
    }

    // ── Archived project update restriction ─────────────────────────────────

    @Nested
    @DisplayName("ARCHIVED project update restriction")
    class ArchivedUpdateRestriction {

        @Test
        @DisplayName("should reject PATCH on archived project with 409")
        void shouldRejectUpdateOnArchived() {
            String projectId = createProject("Archived Update");
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            var updateBody = Map.of("name", "Should Fail");
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_ARCHIVED");
        }

        @Test
        @DisplayName("should allow PATCH after restore")
        void shouldAllowUpdateAfterRestore() {
            String projectId = createProject("Restore Then Update");
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/restore", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            var updateBody = Map.of("name", "Updated After Restore");
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("Updated After Restore");
        }
    }

    // ── Archived project detail ─────────────────────────────────────────────

    @Nested
    @DisplayName("ARCHIVED project detail")
    class ArchivedProjectDetail {

        @Test
        @DisplayName("should allow reading archived project detail")
        void shouldAllowReadArchivedDetail() {
            String projectId = createProject("Archived Detail");

            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("ARCHIVED");
            assertThat(data.get("archivedAt")).isNotNull();
        }
    }
}
