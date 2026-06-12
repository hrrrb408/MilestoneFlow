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
 * Integration tests for task status flow (complete/reopen).
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification.
 */
@DisplayName("Task Status Flow IT")
class TaskStatusFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "task-status-flow@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "task-status-flow-ws";

    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Task Status Flow IT User", encodedPassword);

        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");

        // Create workspace
        authHeaders = buildAuthHeaders(accessToken);
        var wsBody = Map.of("name", "Task Status WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Task Status Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create milestone
        authHeaders = buildAuthHeaders(accessToken);
        var msBody = Map.of("title", "Task Status Milestone");
        ResponseEntity<Map> msResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var msData = (Map<String, Object>) msResponse.getBody().get("data");
        milestoneId = (String) msData.get("id");

        authHeaders = buildAuthHeaders(accessToken);
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

    private String createTaskViaApi() {
        var body = Map.of("title", "Status Test Task", "priority", "HIGH");
        ResponseEntity<Map> response = restTemplate.exchange(
                taskBasePath(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── Complete Task ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{taskId}/complete")
    class CompleteTask {

        @Test
        @DisplayName("should complete task and set completed_at/completed_by")
        void shouldCompleteTask() {
            String taskId = createTaskViaApi();

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("COMPLETED");
            assertThat(data.get("completedAt")).isNotNull();

            // Verify DB state
            @SuppressWarnings("unchecked")
            Map<String, Object> dbTask = jdbc.queryForMap(
                    "SELECT status, completed_at, completed_by FROM task WHERE id = ?::uuid", taskId);
            assertThat(dbTask.get("status")).isEqualTo("COMPLETED");
            assertThat(dbTask.get("completed_at")).isNotNull();
            assertThat(dbTask.get("completed_by")).isNotNull();
        }

        @Test
        @DisplayName("should return 409 when completing already COMPLETED task")
        void shouldReturn409WhenAlreadyCompleted() {
            String taskId = createTaskViaApi();

            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("TASK_ALREADY_COMPLETED");
        }
    }

    // ── Reopen Task ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{taskId}/reopen")
    class ReopenTask {

        @Test
        @DisplayName("should reopen completed task and clear completed_at/completed_by")
        void shouldReopenTask() {
            String taskId = createTaskViaApi();

            // Complete first
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Reopen
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("completedAt")).isNull();

            // Verify DB state
            @SuppressWarnings("unchecked")
            Map<String, Object> dbTask = jdbc.queryForMap(
                    "SELECT status, completed_at, completed_by FROM task WHERE id = ?::uuid", taskId);
            assertThat(dbTask.get("status")).isEqualTo("OPEN");
            assertThat(dbTask.get("completed_at")).isNull();
            assertThat(dbTask.get("completed_by")).isNull();
        }

        @Test
        @DisplayName("should return 409 when reopening OPEN task")
        void shouldReturn409WhenNotCompleted() {
            String taskId = createTaskViaApi();

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("TASK_NOT_COMPLETED");
        }
    }

    // ── List Filtering ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /tasks status/priority filtering")
    class ListFiltering {

        @Test
        @DisplayName("default list returns OPEN + COMPLETED tasks")
        void defaultListReturnsAll() {
            String task1 = createTaskViaApi();
            String task2 = createTaskViaApi();

            // Complete task2
            restTemplate.exchange(
                    taskBasePath() + "/" + task2 + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("status=OPEN returns only OPEN tasks")
        void statusOpenReturnsOnlyOpen() {
            String task1 = createTaskViaApi();
            String task2 = createTaskViaApi();

            // Complete task2
            restTemplate.exchange(
                    taskBasePath() + "/" + task2 + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=OPEN", HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("status=COMPLETED returns only COMPLETED tasks")
        void statusCompletedReturnsOnlyCompleted() {
            String task1 = createTaskViaApi();
            String task2 = createTaskViaApi();

            // Complete task2
            restTemplate.exchange(
                    taskBasePath() + "/" + task2 + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=COMPLETED", HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("priority filter still works after status changes")
        void priorityFilterStillWorks() {
            var body1 = Map.of("title", "High Task", "priority", "HIGH");
            var body2 = Map.of("title", "Low Task", "priority", "LOW");
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?priority=HIGH", HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("priority")).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("status + priority combined filter")
        void statusAndPriorityCombined() {
            var body1 = Map.of("title", "High Open Task", "priority", "HIGH");
            var body2 = Map.of("title", "High Completed Task", "priority", "HIGH");
            var body3 = Map.of("title", "Low Task", "priority", "LOW");

            ResponseEntity<Map> r1 = restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body1, authHeaders), Map.class);
            ResponseEntity<Map> r2 = restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders), Map.class);
            restTemplate.exchange(taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body3, authHeaders), Map.class);

            // Complete the second HIGH task
            @SuppressWarnings("unchecked")
            var d2 = (Map<String, Object>) r2.getBody().get("data");
            restTemplate.exchange(
                    taskBasePath() + "/" + d2.get("id") + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Filter: status=OPEN + priority=HIGH
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=OPEN&priority=HIGH", HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("title")).isEqualTo("High Open Task");
        }

        @Test
        @DisplayName("should return 422 for invalid status value")
        void shouldReturn422ForInvalidStatus() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "?status=INVALID", HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ── COMPLETED Task Restrictions ───────────────────────────────────────

    @Nested
    @DisplayName("COMPLETED task restrictions")
    class CompletedTaskRestrictions {

        @Test
        @DisplayName("COMPLETED task detail is readable")
        void completedTaskDetailIsReadable() {
            String taskId = createTaskViaApi();
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("COMPLETED task PATCH returns 409")
        void completedTaskPatchReturns409() {
            String taskId = createTaskViaApi();
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            var body = Map.of("title", "Updated Title");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("TASK_COMPLETED");
        }

        @Test
        @DisplayName("reopen then PATCH succeeds")
        void reopenThenPatchSucceeds() {
            String taskId = createTaskViaApi();
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            var body = Map.of("title", "Updated After Reopen");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(body, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Updated After Reopen");
        }
    }

    // ── ARCHIVED Project / COMPLETED Milestone Restrictions ───────────────

    @Nested
    @DisplayName("Cross-constraint restrictions")
    class CrossConstraintRestrictions {

        @Test
        @DisplayName("ARCHIVED project: complete returns 409")
        void archivedProjectCompleteReturns409() {
            String taskId = createTaskViaApi();

            // Archive the project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("PROJECT_ARCHIVED");
        }

        @Test
        @DisplayName("ARCHIVED project: reopen returns 409")
        void archivedProjectReopenReturns409() {
            String taskId = createTaskViaApi();
            // Complete first
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Archive the project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("PROJECT_ARCHIVED");
        }

        @Test
        @DisplayName("COMPLETED milestone: complete returns 409")
        void completedMilestoneCompleteReturns409() {
            String taskId = createTaskViaApi();

            // Complete the milestone
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("MILESTONE_COMPLETED");
        }

        @Test
        @DisplayName("COMPLETED milestone: reopen returns 409")
        void completedMilestoneReopenReturns409() {
            String taskId = createTaskViaApi();
            // Complete task first
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Complete the milestone
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) response.getBody();
            assertThat(error.get("code")).isEqualTo("MILESTONE_COMPLETED");
        }
    }
}
