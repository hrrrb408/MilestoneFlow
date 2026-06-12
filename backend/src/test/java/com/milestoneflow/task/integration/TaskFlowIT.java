package com.milestoneflow.task.integration;

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
 * Integration tests for task CRUD flows.
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification.
 */
@DisplayName("Task Flow IT")
class TaskFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "task-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "task-flow-ws";

    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private HttpHeaders authHeaders;
    private HttpHeaders authHeadersOnly;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Task Flow IT User", encodedPassword);

        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        authHeaders = buildAuthHeaders(accessToken);
        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Task Test Workspace", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Task Test Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        assertThat(projResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create milestone
        authHeaders = buildAuthHeaders(accessToken);
        var msBody = Map.of("title", "Task Test Milestone");
        ResponseEntity<Map> msResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones", HttpMethod.POST,
                new HttpEntity<>(msBody, authHeaders), Map.class);
        assertThat(msResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var msData = (Map<String, Object>) msResponse.getBody().get("data");
        milestoneId = (String) msData.get("id");

        authHeaders = buildAuthHeaders(accessToken);
        authHeadersOnly = new HttpHeaders();
        authHeadersOnly.add("Cookie", accessToken);
    }

    private void cleanAll() {
        String norm = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM task WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
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

    private String taskBasePath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId
                + "/milestones/" + milestoneId + "/tasks";
    }

    // ── POST /tasks ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /tasks")
    class CreateTask {

        @Test
        @DisplayName("should create task with 201")
        void shouldCreateTask() {
            var body = Map.of(
                    "title", "Implement auth",
                    "description", "Add login and registration",
                    "priority", "HIGH",
                    "dueDate", "2026-07-15"
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Implement auth");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("priority")).isEqualTo("HIGH");
            assertThat(data.get("workspaceId")).isEqualTo(workspaceId);
            assertThat(data.get("projectId")).isEqualTo(projectId);
            assertThat(data.get("milestoneId")).isEqualTo(milestoneId);
            assertThat(data.get("dueDate")).isEqualTo("2026-07-15");
            assertThat(data.get("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("should default priority to MEDIUM when not specified")
        void shouldDefaultPriority() {
            var body = Map.of("title", "Default Priority Task");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("priority")).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("should persist task in database")
        void shouldPersistTask() {
            var body = Map.of("title", "DB Test Task");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task WHERE milestone_id = ?::uuid AND title = 'DB Test Task'",
                    Integer.class, milestoneId);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should reject blank title with 422")
        void shouldRejectBlankTitle() {
            var body = Map.of("title", "");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should reject title too long with 422")
        void shouldRejectTitleTooLong() {
            var body = Map.of("title", "x".repeat(161));

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should reject archived project with 409")
        void shouldRejectArchivedProject() {
            // Archive project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            var body = Map.of("title", "Archived Project Task");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject completed milestone with 409")
        void shouldRejectCompletedMilestone() {
            // Complete milestone
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            var body = Map.of("title", "Completed MS Task");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ── GET /tasks ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /tasks")
    class ListTasks {

        @Test
        @DisplayName("should list tasks in milestone")
        void shouldListTasks() {
            var body1 = Map.of("title", "Task A");
            var body2 = Map.of("title", "Task B");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no tasks")
        void shouldReturnEmptyList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
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
            var body = Map.of("title", "Open Task");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=OPEN", HttpMethod.GET,
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
            var body = Map.of("title", "Open Task");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=COMPLETED", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("should filter by priority=HIGH")
        void shouldFilterByPriority() {
            var body1 = Map.of("title", "High Task", "priority", "HIGH");
            var body2 = Map.of("title", "Low Task", "priority", "LOW");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?priority=HIGH", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("priority")).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("should filter by status and priority combined")
        void shouldFilterByStatusAndPriority() {
            var body1 = Map.of("title", "High Open", "priority", "HIGH");
            var body2 = Map.of("title", "Low Open", "priority", "LOW");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=OPEN&priority=HIGH", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("title")).isEqualTo("High Open");
        }
    }

    // ── GET /tasks/{id} ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /tasks/{id}")
    class GetTask {

        @Test
        @DisplayName("should return task detail")
        void shouldReturnTaskDetail() {
            var body = Map.of("title", "Detail Test", "description", "A test task",
                    "priority", "HIGH");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Detail Test");
            assertThat(data.get("description")).isEqualTo("A test task");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("priority")).isEqualTo("HIGH");
            assertThat(data.get("milestoneId")).isEqualTo(milestoneId);
        }

        @Test
        @DisplayName("should return 404 for non-existent task")
        void shouldReturn404ForNonExistent() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + randomId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code")).isEqualTo("TASK_NOT_FOUND");
        }

        @Test
        @DisplayName("should return 404 for cross-milestone task")
        void shouldReturn404ForCrossMilestone() {
            // Create task in current milestone
            var body = Map.of("title", "Cross-Milestone Task");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            // Create another milestone
            var msBody = Map.of("title", "Other Milestone");
            ResponseEntity<Map> otherMsResponse = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                    HttpMethod.POST, new HttpEntity<>(msBody, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var otherMsData = (Map<String, Object>) otherMsResponse.getBody().get("data");
            String otherMilestoneId = (String) otherMsData.get("id");

            // Try to access task from other milestone
            String otherPath = "/workspaces/" + workspaceId + "/projects/" + projectId
                    + "/milestones/" + otherMilestoneId + "/tasks/" + taskId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    otherPath, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 for cross-project task")
        void shouldReturn404ForCrossProject() {
            // Create task
            var body = Map.of("title", "Cross-Project Task");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            // Create another project and milestone
            var projBody = Map.of("name", "Other Project");
            ResponseEntity<Map> otherProjResponse = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                    new HttpEntity<>(projBody, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var otherProjData = (Map<String, Object>) otherProjResponse.getBody().get("data");
            String otherProjectId = (String) otherProjData.get("id");

            var msBody = Map.of("title", "Some Milestone");
            ResponseEntity<Map> otherMsResponse = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + otherProjectId + "/milestones",
                    HttpMethod.POST, new HttpEntity<>(msBody, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var otherMsData = (Map<String, Object>) otherMsResponse.getBody().get("data");
            String otherMilestoneId = (String) otherMsData.get("id");

            // Try to access from other project
            String otherPath = "/workspaces/" + workspaceId + "/projects/" + otherProjectId
                    + "/milestones/" + otherMilestoneId + "/tasks/" + taskId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    otherPath, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 for cross-workspace task")
        void shouldReturn404ForCrossWorkspace() {
            // Create task
            var body = Map.of("title", "Cross-WS Task");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            // Use a random workspace UUID
            String fakeWs = UUID.randomUUID().toString();
            String otherPath = "/workspaces/" + fakeWs + "/projects/" + projectId
                    + "/milestones/" + milestoneId + "/tasks/" + taskId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    otherPath, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── PATCH /tasks/{id} ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /tasks/{id}")
    class UpdateTask {

        @Test
        @DisplayName("should update task title")
        void shouldUpdateTaskTitle() {
            var body = Map.of("title", "Original Task");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            var updateBody = Map.of("title", "Updated Task");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Updated Task");
        }

        @Test
        @DisplayName("should update task priority and dueDate")
        void shouldUpdatePriorityAndDueDate() {
            var body = Map.of("title", "Priority Test");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            var updateBody = Map.of("priority", "HIGH", "dueDate", "2026-08-15");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("priority")).isEqualTo("HIGH");
            assertThat(data.get("dueDate")).isEqualTo("2026-08-15");
        }

        @Test
        @DisplayName("should return 404 for non-existent task update")
        void shouldReturn404ForNonExistentUpdate() {
            String randomId = UUID.randomUUID().toString();
            var updateBody = Map.of("title", "Ghost");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + randomId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should reject archived project update with 409")
        void shouldRejectArchivedProjectUpdate() {
            var body = Map.of("title", "To Update");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            // Archive project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            var updateBody = Map.of("title", "Updated");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject completed milestone update with 409")
        void shouldRejectCompletedMilestoneUpdate() {
            var body = Map.of("title", "To Update");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var createData = (Map<String, Object>) createResponse.getBody().get("data");
            String taskId = (String) createData.get("id");

            // Complete milestone
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            var updateBody = Map.of("title", "Updated");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ── Read access under archived/completed ──────────────────────────────

    @Nested
    @DisplayName("Read access under archived/completed")
    class ReadAccess {

        @Test
        @DisplayName("should still list tasks under archived project")
        void shouldListTasksUnderArchivedProject() {
            var body = Map.of("title", "Archived Project Task");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            // Archive project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("should still list tasks under completed milestone")
        void shouldListTasksUnderCompletedMilestone() {
            var body = Map.of("title", "Completed MS Task");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);

            // Complete milestone
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
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
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject unauthenticated GET with 401")
        void shouldRejectUnauthenticatedGet() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
