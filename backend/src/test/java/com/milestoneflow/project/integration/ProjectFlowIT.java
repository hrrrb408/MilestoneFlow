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
 * Integration tests for project CRUD flows.
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification.
 */
@DisplayName("Project Flow IT")
class ProjectFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "project-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "project-test-ws";
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
            """, EMAIL, EMAIL.toLowerCase(), "Project Flow IT User", encodedPassword);

        // Login to get access token cookie
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        String xsrfCookie = null;
        String csrfToken = "";

        // Get CSRF token via /auth/me
        var csrfHeaders = new HttpHeaders();
        csrfHeaders.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);
        xsrfCookie = extractCookieFromResponse(csrfResponse, "XSRF-TOKEN=");
        if (xsrfCookie != null) {
            csrfToken = xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
        }

        // Build auth headers with both cookies and CSRF
        authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.add("Cookie", accessToken);
        if (xsrfCookie != null) {
            authHeaders.add("Cookie", xsrfCookie.split(";")[0]);
        }
        authHeaders.add("X-XSRF-TOKEN", csrfToken);

        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);

        // Create a workspace for project tests
        var wsBody = Map.of("name", "Project Test Workspace", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);

        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(wsResponse.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        assertThat(wsData).isNotNull();
        workspaceId = (String) wsData.get("id");
        assertThat(workspaceId).isNotNull();

        // Refresh authHeaders for subsequent requests (CSRF cookie may have changed)
        authHeaders = buildAuthHeaders(accessToken);
    }

    private void cleanAll() {
        String norm = EMAIL.toLowerCase();
        // created_by is NULL in tests (AuditorAware returns empty for JDBC-inserted users)
        // so clean workspace by its fixed slug instead
        jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE slug = ?", WS_SLUG);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
    }

    private String extractCookie(ResponseEntity<?> response, String prefix) {
        var setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return null;
        return setCookies.stream()
                .filter(c -> c.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    private String extractCookieFromResponse(ResponseEntity<?> response, String prefix) {
        var setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return null;
        return setCookies.stream()
                .filter(c -> c.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    private HttpHeaders buildAuthHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessToken);

        // Get fresh CSRF token
        var csrfHeaders = new HttpHeaders();
        csrfHeaders.add("Cookie", accessToken);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);
        String xsrfCookie = extractCookieFromResponse(csrfResponse, "XSRF-TOKEN=");
        if (xsrfCookie != null) {
            String csrfToken = xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
            headers.add("Cookie", xsrfCookie.split(";")[0]);
            headers.add("X-XSRF-TOKEN", csrfToken);
        }
        return headers;
    }

    private String projectBasePath() {
        return "/workspaces/" + workspaceId + "/projects";
    }

    // ── POST /projects ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /workspaces/{id}/projects")
    class CreateProject {

        @Test
        @DisplayName("should create project with 201")
        void shouldCreateProject() {
            var body = Map.of(
                    "name", "Pilot MVP",
                    "description", "MilestoneFlow Pilot MVP project",
                    "startDate", "2026-06-12",
                    "targetDate", "2026-07-12"
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("Pilot MVP");
            assertThat(data.get("status")).isEqualTo("ACTIVE");
            assertThat(data.get("workspaceId")).isEqualTo(workspaceId);
            assertThat(data.get("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("should persist project in database")
        void shouldPersistProject() {
            var body = Map.of("name", "DB Test Project", "description", "Test");
            restTemplate.exchange(projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE workspace_id = ?::uuid AND name = 'DB Test Project'",
                    Integer.class, workspaceId);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should reject blank name with 422")
        void shouldRejectBlankName() {
            var body = Map.of("name", "");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should reject invalid date range with 422")
        void shouldRejectInvalidDateRange() {
            var body = Map.of(
                    "name", "Invalid Dates",
                    "startDate", "2026-07-12",
                    "targetDate", "2026-06-12"
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_INVALID_DATE_RANGE");
        }

        @Test
        @DisplayName("should create project without dates")
        void shouldCreateWithoutDates() {
            var body = Map.of("name", "No Dates Project");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("startDate")).isNull();
            assertThat(data.get("targetDate")).isNull();
        }
    }

    // ── GET /projects ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects")
    class ListProjects {

        @Test
        @DisplayName("should list projects in workspace")
        void shouldListProjects() {
            // Create two projects
            var body1 = Map.of("name", "Project A");
            var body2 = Map.of("name", "Project B");
            restTemplate.exchange(projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no projects")
        void shouldReturnEmptyList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }
    }

    // ── GET /projects/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects/{id}")
    class GetProject {

        @Test
        @DisplayName("should return project detail")
        void shouldReturnProjectDetail() {
            var body = Map.of("name", "Detail Test", "description", "A test project");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String projectId = (String) createData.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("Detail Test");
            assertThat(data.get("description")).isEqualTo("A test project");
            assertThat(data.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should return 404 for non-existent project")
        void shouldReturn404ForNonExistent() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + randomId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_NOT_FOUND");
        }
    }

    // ── PATCH /projects/{id} ──────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /workspaces/{id}/projects/{id}")
    class UpdateProject {

        @Test
        @DisplayName("should update project name")
        void shouldUpdateProjectName() {
            var body = Map.of("name", "Original Project");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String projectId = (String) createData.get("id");

            var updateBody = Map.of("name", "Updated Project");
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("Updated Project");
        }

        @Test
        @DisplayName("should update project dates")
        void shouldUpdateProjectDates() {
            var body = Map.of("name", "Date Update Test");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String projectId = (String) createData.get("id");

            var updateBody = Map.of(
                    "startDate", "2026-06-15",
                    "targetDate", "2026-08-01"
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("startDate")).isEqualTo("2026-06-15");
            assertThat(data.get("targetDate")).isEqualTo("2026-08-01");
        }

        @Test
        @DisplayName("should return 404 for non-existent project update")
        void shouldReturn404ForNonExistentUpdate() {
            String randomId = UUID.randomUUID().toString();
            var updateBody = Map.of("name", "Ghost");

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + randomId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Unauthenticated access ────────────────────────────────────────────

    @Nested
    @DisplayName("Unauthenticated access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("should reject unauthenticated POST with 401")
        void shouldRejectUnauthenticatedPost() {
            var body = Map.of("name", "Test");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject unauthenticated GET with 401")
        void shouldRejectUnauthenticatedGet() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
