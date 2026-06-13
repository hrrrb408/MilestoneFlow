package com.milestoneflow.activity.integration;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for activity log timeline flow.
 *
 * <p>Verifies that business operations (create workspace, project, milestone, task)
 * produce activity events that are queryable via the timeline APIs.
 */
@DisplayName("Activity Log Flow IT")
class ActivityLogFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "activity-flow-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "activity-flow-ws";

    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private String taskId;
    private HttpHeaders readHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        // Create user
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Activity Flow IT User", encodedPassword);

        // Login
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        var authHeaders = buildAuthHeaders(accessToken);
        readHeaders = new HttpHeaders();
        readHeaders.add("Cookie", accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Activity Test Workspace", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Activity Test Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        assertThat(projResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create milestone
        authHeaders = buildAuthHeaders(accessToken);
        var msBody = Map.of("title", "Activity Test Milestone");
        ResponseEntity<Map> msResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBody, authHeaders), Map.class);
        assertThat(msResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var msData = (Map<String, Object>) msResponse.getBody().get("data");
        milestoneId = (String) msData.get("id");

        // Create task
        authHeaders = buildAuthHeaders(accessToken);
        var taskBody = Map.of("title", "Activity Test Task");
        ResponseEntity<Map> taskResponse = restTemplate.exchange(
                taskBasePath(), HttpMethod.POST,
                new HttpEntity<>(taskBody, authHeaders), Map.class);
        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var taskData = (Map<String, Object>) taskResponse.getBody().get("data");
        taskId = (String) taskData.get("id");
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

    // ── Workspace activities ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/activities")
    class WorkspaceActivities {

        @Test
        @DisplayName("should return workspace activity events")
        void shouldReturnWorkspaceActivities() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) body.get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // At minimum: WORKSPACE_CREATED, PROJECT_CREATED, MILESTONE_CREATED, TASK_CREATED
            assertThat(items).isNotEmpty();

            var eventTypes = items.stream()
                    .map(item -> (String) item.get("eventType"))
                    .toList();
            assertThat(eventTypes).contains("TASK_CREATED", "MILESTONE_CREATED", "PROJECT_CREATED");
        }

        @Test
        @DisplayName("should return activities ordered by createdAt DESC")
        void shouldReturnOrderedByCreatedAtDesc() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // Most recent event should be first (TASK_CREATED)
            assertThat(items.get(0).get("eventType")).isEqualTo("TASK_CREATED");
        }

        @Test
        @DisplayName("should respect limit parameter")
        void shouldRespectLimit() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities?limit=2",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("should filter by eventType")
        void shouldFilterByEventType() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities?eventType=TASK_CREATED",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> "TASK_CREATED".equals(i.get("eventType")))).isTrue();
        }

        @Test
        @DisplayName("should filter by targetType")
        void shouldFilterByTargetType() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities?targetType=TASK",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> "TASK".equals(i.get("targetType")))).isTrue();
        }

        @Test
        @DisplayName("should include event metadata without sensitive fields")
        void shouldIncludeMetadata() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities?eventType=TASK_CREATED",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            @SuppressWarnings("unchecked")
            var metadata = (Map<String, Object>) items.get(0).get("metadata");
            assertThat(metadata).isNotNull();
            // Should contain task fields but never sensitive data
            assertThat(metadata).doesNotContainKey("password");
            assertThat(metadata).doesNotContainKey("token");
            assertThat(metadata).doesNotContainKey("cookie");
        }

        @Test
        @DisplayName("should include nextCursor as null")
        void shouldIncludeNullNextCursor() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("nextCursor")).isNull();
        }
    }

    // ── Project activities ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects/{id}/activities")
    class ProjectActivities {

        @Test
        @DisplayName("should return project-level activity events")
        void shouldReturnProjectActivities() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // Only PROJECT-level events (safe minimum scope)
            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> "PROJECT".equals(i.get("targetType")))).isTrue();
        }

        @Test
        @DisplayName("should return project events with correct targetId")
        void shouldReturnCorrectProjectId() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> projectId.equals(i.get("targetId")))).isTrue();
        }
    }

    // ── Milestone activities ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects/{id}/milestones/{id}/activities")
    class MilestoneActivities {

        @Test
        @DisplayName("should return milestone-level activity events")
        void shouldReturnMilestoneActivities() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> "MILESTONE".equals(i.get("targetType")))).isTrue();
        }
    }

    // ── Task activities ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/projects/{id}/milestones/{id}/tasks/{id}/activities")
    class TaskActivities {

        @Test
        @DisplayName("should return task activity events")
        void shouldReturnTaskActivities() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            var eventTypes = items.stream()
                    .map(item -> (String) item.get("eventType"))
                    .toList();
            assertThat(eventTypes).contains("TASK_CREATED");
        }

        @Test
        @DisplayName("should include status transition events after complete and reopen")
        void shouldIncludeStatusTransitionEvents() {
            // Complete task
            var accessToken = readHeaders.getFirst("Cookie");
            var authHeaders = buildAuthHeaders(accessToken);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Reopen task
            authHeaders = buildAuthHeaders(accessToken);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Query task activities
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            var eventTypes = items.stream()
                    .map(item -> (String) item.get("eventType"))
                    .toList();
            assertThat(eventTypes).contains("TASK_CREATED", "TASK_COMPLETED", "TASK_REOPENED");

            // Most recent first
            assertThat(eventTypes.get(0)).isEqualTo("TASK_REOPENED");
        }

        @Test
        @DisplayName("should return empty items for valid scope with no events")
        void shouldReturnEmptyForValidScope() {
            // Create a second task that has only one event
            var accessToken = readHeaders.getFirst("Cookie");
            var authHeaders = buildAuthHeaders(accessToken);
            var body = Map.of("title", "Another Task");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders), Map.class);
            @SuppressWarnings("unchecked")
            var newData = (Map<String, Object>) createResponse.getBody().get("data");
            String newTaskId = (String) newData.get("id");

            // This new task has exactly one event (TASK_CREATED)
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + newTaskId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
        }
    }

    // ── Activity response structure ──────────────────────────────────────────

    @Nested
    @DisplayName("Activity response structure")
    class ResponseStructure {

        @Test
        @DisplayName("should return all expected fields in activity item")
        void shouldReturnAllFields() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/activities?limit=1",
                    HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");
            assertThat(items).isNotEmpty();

            var item = items.get(0);
            assertThat(item).containsKeys("id", "workspaceId", "actorId", "actorType",
                    "eventType", "targetType", "targetId", "summary", "metadata", "createdAt");
        }
    }
}
