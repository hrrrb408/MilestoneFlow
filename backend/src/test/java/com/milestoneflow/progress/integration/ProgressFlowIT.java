package com.milestoneflow.progress.integration;

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
 * Integration tests for progress read model flows.
 *
 * <p>Tests the full stack from HTTP request through to database aggregation,
 * verifying correct completion rate calculation and data isolation.
 */
@DisplayName("Progress Flow IT")
class ProgressFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "progress-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "progress-flow-ws";

    private String workspaceId;
    private String projectId;
    private String milestone1Id;
    private String milestone2Id;
    private String milestone3Id;
    private HttpHeaders authHeaders;
    private HttpHeaders readHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Progress Flow IT User", encodedPassword);

        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        authHeaders = buildAuthHeaders(accessToken);
        readHeaders = buildReadHeaders(accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Progress WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Progress Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create 3 milestones
        authHeaders = buildAuthHeaders(accessToken);
        milestone1Id = createMilestone("Milestone A", "2026-07-01");
        milestone2Id = createMilestone("Milestone B", "2026-08-01");
        milestone3Id = createMilestone("Milestone C", null);

        authHeaders = buildAuthHeaders(accessToken);
        readHeaders = buildReadHeaders(accessToken);
    }

    // ── Project Progress ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /projects/{projectId}/progress")
    class GetProjectProgress {

        @Test
        @DisplayName("should return 0.00% when no tasks exist")
        void shouldReturnZeroWhenNoTasks() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectProgressPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data.get("totalTasks")).isEqualTo(0);
            assertThat(data.get("completedTasks")).isEqualTo(0);
            assertThat(data.get("openTasks")).isEqualTo(0);
            assertThat(data.get("completionRate")).isEqualTo(0.00);
            assertThat(data.get("totalMilestones")).isEqualTo(3);
            assertThat(data.get("completedMilestones")).isEqualTo(0);
            assertThat(data.get("openMilestones")).isEqualTo(3);
        }

        @Test
        @DisplayName("should aggregate tasks across all milestones")
        void shouldAggregateAcrossMilestones() {
            // Milestone A: 3 tasks, 2 completed
            createTask(milestone1Id, "Task A1", null);
            createTask(milestone1Id, "Task A2", null);
            createTask(milestone1Id, "Task A3", null);
            String taskA1 = getTaskId(milestone1Id, "Task A1");
            String taskA2 = getTaskId(milestone1Id, "Task A2");
            completeTask(milestone1Id, taskA1);
            completeTask(milestone1Id, taskA2);

            // Milestone B: 2 tasks, 0 completed
            createTask(milestone2Id, "Task B1", null);
            createTask(milestone2Id, "Task B2", null);

            // Project total: 5 tasks, 2 completed → 40.00%

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectProgressPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data.get("totalTasks")).isEqualTo(5);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("openTasks")).isEqualTo(3);
            assertThat(data.get("completionRate")).isEqualTo(40.00);
        }

        @Test
        @DisplayName("should return 100.00% when all tasks completed")
        void shouldReturn100WhenAllCompleted() {
            createTask(milestone1Id, "Task 1", null);
            String taskId = getTaskId(milestone1Id, "Task 1");
            completeTask(milestone1Id, taskId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectProgressPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data.get("completionRate")).isEqualTo(100.00);
            assertThat(data.get("openTasks")).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 404 for non-existent project")
        void shouldReturn404ForNonExistentProject() {
            String fakeProject = UUID.randomUUID().toString();
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + fakeProject + "/progress",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should work for archived project")
        void shouldWorkForArchivedProject() {
            createTask(milestone1Id, "Task 1", null);

            // Archive project
            authHeaders = buildAuthHeaders(getAccessToken());
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Progress should still be readable
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectProgressPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("totalTasks")).isEqualTo(1);
        }
    }

    // ── Milestone Progress ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /milestones/{milestoneId}/progress")
    class GetMilestoneProgress {

        @Test
        @DisplayName("should return milestone progress with correct counts")
        void shouldReturnMilestoneProgress() {
            createTask(milestone1Id, "Task 1", null);
            createTask(milestone1Id, "Task 2", null);
            createTask(milestone1Id, "Task 3", null);
            String task1 = getTaskId(milestone1Id, "Task 1");
            String task2 = getTaskId(milestone1Id, "Task 2");
            completeTask(milestone1Id, task1);
            completeTask(milestone1Id, task2);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneProgressPath(milestone1Id), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data.get("milestoneId")).isEqualTo(milestone1Id);
            assertThat(data.get("milestoneStatus")).isEqualTo("OPEN");
            assertThat(data.get("totalTasks")).isEqualTo(3);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("openTasks")).isEqualTo(1);
            assertThat(data.get("completionRate")).isEqualTo(66.67);
        }

        @Test
        @DisplayName("should return 0.00% when milestone has no tasks")
        void shouldReturnZeroWhenNoTasks() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneProgressPath(milestone3Id), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data.get("totalTasks")).isEqualTo(0);
            assertThat(data.get("completionRate")).isEqualTo(0.00);
        }

        @Test
        @DisplayName("should return 404 for non-existent milestone")
        void shouldReturn404ForNonExistentMilestone() {
            String fakeMilestone = UUID.randomUUID().toString();
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneProgressPath(fakeMilestone), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Milestone Progress List ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /milestones/progress")
    class ListMilestoneProgress {

        @Test
        @DisplayName("should return progress for each milestone")
        void shouldReturnProgressForAllMilestones() {
            // Milestone A: 3 tasks, 2 completed → 66.67
            createTask(milestone1Id, "Task A1", null);
            createTask(milestone1Id, "Task A2", null);
            createTask(milestone1Id, "Task A3", null);
            String taskA1 = getTaskId(milestone1Id, "Task A1");
            String taskA2 = getTaskId(milestone1Id, "Task A2");
            completeTask(milestone1Id, taskA1);
            completeTask(milestone1Id, taskA2);

            // Milestone B: 2 tasks, 0 completed → 0.00
            createTask(milestone2Id, "Task B1", null);
            createTask(milestone2Id, "Task B2", null);

            // Milestone C: 0 tasks → 0.00

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneProgressListPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");

            assertThat(items).hasSize(3);

            // Milestone A
            Map<String, Object> msA = items.get(0);
            assertThat(msA.get("milestoneId")).isEqualTo(milestone1Id);
            assertThat(msA.get("totalTasks")).isEqualTo(3);
            assertThat(msA.get("completedTasks")).isEqualTo(2);
            assertThat(msA.get("openTasks")).isEqualTo(1);
            assertThat(msA.get("completionRate")).isEqualTo(66.67);

            // Milestone B
            Map<String, Object> msB = items.get(1);
            assertThat(msB.get("milestoneId")).isEqualTo(milestone2Id);
            assertThat(msB.get("totalTasks")).isEqualTo(2);
            assertThat(msB.get("completedTasks")).isEqualTo(0);
            assertThat(msB.get("openTasks")).isEqualTo(2);
            assertThat(msB.get("completionRate")).isEqualTo(0.00);

            // Milestone C (no tasks)
            Map<String, Object> msC = items.get(2);
            assertThat(msC.get("milestoneId")).isEqualTo(milestone3Id);
            assertThat(msC.get("totalTasks")).isEqualTo(0);
            assertThat(msC.get("completionRate")).isEqualTo(0.00);
        }

        @Test
        @DisplayName("should return empty list when no milestones")
        void shouldReturnEmptyWhenNoMilestones() {
            // Create a separate project with no milestones
            authHeaders = buildAuthHeaders(getAccessToken());
            var projBody = Map.of("name", "Empty Project");
            ResponseEntity<Map> projResponse = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                    new HttpEntity<>(projBody, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var projData = (Map<String, Object>) projResponse.getBody().get("data");
            String emptyProjectId = (String) projData.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + emptyProjectId
                            + "/milestones/progress",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }
    }

    // ── Accuracy ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Progress accuracy")
    class ProgressAccuracy {

        @Test
        @DisplayName("project uses task aggregation not milestone average")
        void projectUsesTaskAggregation() {
            // Milestone A: 3 tasks, 2 completed
            createTask(milestone1Id, "A1", null);
            createTask(milestone1Id, "A2", null);
            createTask(milestone1Id, "A3", null);
            completeTask(milestone1Id, getTaskId(milestone1Id, "A1"));
            completeTask(milestone1Id, getTaskId(milestone1Id, "A2"));

            // Milestone B: 2 tasks, 0 completed
            createTask(milestone2Id, "B1", null);
            createTask(milestone2Id, "B2", null);

            // Project: 5 tasks, 2 completed → 40.00 (not avg of 66.67 and 0.00 = 33.33)
            ResponseEntity<Map> response = restTemplate.exchange(
                    projectProgressPath(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("completionRate")).isEqualTo(40.00);
        }

        @Test
        @DisplayName("openTasks + completedTasks equals totalTasks")
        void taskCountsAreConsistent() {
            createTask(milestone1Id, "T1", null);
            createTask(milestone1Id, "T2", null);
            createTask(milestone1Id, "T3", null);
            completeTask(milestone1Id, getTaskId(milestone1Id, "T1"));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneProgressPath(milestone1Id), HttpMethod.GET,
                    new HttpEntity<>(readHeaders), Map.class);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            int total = ((Number) data.get("totalTasks")).intValue();
            int completed = ((Number) data.get("completedTasks")).intValue();
            int open = ((Number) data.get("openTasks")).intValue();

            assertThat(completed + open).isEqualTo(total);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    private String getAccessToken() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        return extractCookie(loginResponse, "MF_ACCESS=");
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

    private HttpHeaders buildReadHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessToken);
        return headers;
    }

    private String projectProgressPath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId + "/progress";
    }

    private String milestoneProgressPath(String milestoneId) {
        return "/workspaces/" + workspaceId + "/projects/" + projectId
                + "/milestones/" + milestoneId + "/progress";
    }

    private String milestoneProgressListPath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId
                + "/milestones/progress";
    }

    private String createMilestone(String title, String dueDate) {
        var body = dueDate != null
                ? Map.of("title", title, "dueDate", dueDate)
                : Map.of("title", title);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    private void createTask(String msId, String title, String dueDate) {
        var body = dueDate != null
                ? Map.of("title", title, "dueDate", dueDate)
                : Map.of("title", title);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + msId + "/tasks",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String getTaskId(String msId, String title) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + msId + "/tasks",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        var items = (java.util.List<Map<String, Object>>) data.get("items");
        return items.stream()
                .filter(t -> title.equals(t.get("title")))
                .map(t -> (String) t.get("id"))
                .findFirst()
                .orElseThrow();
    }

    private void completeTask(String msId, String taskId) {
        restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + msId + "/tasks/" + taskId + "/complete",
                HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);
    }
}
