package com.milestoneflow.milestone.integration;

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
 * Integration tests for milestone CRUD flows.
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification.
 */
@DisplayName("Milestone Flow IT")
class MilestoneFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "milestone-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "milestone-test-ws";

    private String workspaceId;
    private String projectId;
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
            """, EMAIL, EMAIL.toLowerCase(), "Milestone Flow IT User", encodedPassword);

        // Login to get access token cookie
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
        var wsBody = Map.of("name", "Milestone Test Workspace", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Test Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        assertThat(projResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        authHeaders = buildAuthHeaders(accessToken);
        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);
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
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
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

    private String milestoneBasePath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones";
    }

    // ── POST /milestones ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /milestones")
    class CreateMilestone {

        @Test
        @DisplayName("should create milestone with 201")
        void shouldCreateMilestone() {
            var body = Map.of(
                    "title", "MVP Authentication Completed",
                    "description", "Finish auth, workspace and project foundations",
                    "dueDate", "2026-07-01"
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("MVP Authentication Completed");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("workspaceId")).isEqualTo(workspaceId);
            assertThat(data.get("projectId")).isEqualTo(projectId);
            assertThat(data.get("dueDate")).isEqualTo("2026-07-01");
            assertThat(data.get("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("should persist milestone in database")
        void shouldPersistMilestone() {
            var body = Map.of("title", "DB Test Milestone", "description", "Test");
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM milestone WHERE project_id = ?::uuid AND title = 'DB Test Milestone'",
                    Integer.class, projectId);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should create milestone without dueDate")
        void shouldCreateWithoutDueDate() {
            var body = Map.of("title", "No Due Date");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("dueDate")).isNull();
        }

        @Test
        @DisplayName("should reject blank title with 422")
        void shouldRejectBlankTitle() {
            var body = Map.of("title", "");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should reject title too long with 422")
        void shouldRejectTitleTooLong() {
            var body = Map.of("title", "x".repeat(181));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ── GET /milestones ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /milestones")
    class ListMilestones {

        @Test
        @DisplayName("should list milestones in project")
        void shouldListMilestones() {
            var body1 = Map.of("title", "Milestone A");
            var body2 = Map.of("title", "Milestone B");
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no milestones")
        void shouldReturnEmptyList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("should filter by status=OPEN")
        void shouldFilterByStatus() {
            var body = Map.of("title", "Open Milestone");
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=OPEN", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("should return empty for status=COMPLETED when none completed")
        void shouldReturnEmptyForCompleted() {
            var body = Map.of("title", "Open Milestone");
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=COMPLETED", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }
    }

    // ── GET /milestones/{id} ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /milestones/{id}")
    class GetMilestone {

        @Test
        @DisplayName("should return milestone detail")
        void shouldReturnMilestoneDetail() {
            var body = Map.of("title", "Detail Test", "description", "A test milestone");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) createData.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Detail Test");
            assertThat(data.get("description")).isEqualTo("A test milestone");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("projectId")).isEqualTo(projectId);
        }

        @Test
        @DisplayName("should return 404 for non-existent milestone")
        void shouldReturn404ForNonExistent() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + randomId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code")).isEqualTo("MILESTONE_NOT_FOUND");
        }
    }

    // ── PATCH /milestones/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /milestones/{id}")
    class UpdateMilestone {

        @Test
        @DisplayName("should update milestone title")
        void shouldUpdateMilestoneTitle() {
            var body = Map.of("title", "Original Milestone");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) createData.get("id");

            var updateBody = Map.of("title", "Updated Milestone");
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Updated Milestone");
        }

        @Test
        @DisplayName("should update milestone dueDate")
        void shouldUpdateMilestoneDueDate() {
            var body = Map.of("title", "Date Update Test");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) createData.get("id");

            var updateBody = Map.of("dueDate", "2026-08-15");
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("dueDate")).isEqualTo("2026-08-15");
        }

        @Test
        @DisplayName("should return 404 for non-existent milestone update")
        void shouldReturn404ForNonExistentUpdate() {
            String randomId = UUID.randomUUID().toString();
            var updateBody = Map.of("title", "Ghost");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + randomId, HttpMethod.PATCH,
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
            var body = Map.of("title", "Test");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject unauthenticated GET with 401")
        void shouldRejectUnauthenticatedGet() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
