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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit integration tests for task status operations.
 *
 * <p>Verifies that TASK_COMPLETED and TASK_REOPENED audit events
 * are written to the audit_event table with correct metadata.
 */
@DisplayName("Task Status Audit IT")
class TaskStatusAuditIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "task-status-audit@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "task-status-audit-ws";

    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private HttpHeaders authHeaders;
    private UUID userId;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Task Status Audit IT User", encodedPassword);

        userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE email_normalized = ?",
                UUID.class, EMAIL.toLowerCase());

        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        String accessToken = extractCookie(loginResponse, "MF_ACCESS=");
        authHeaders = buildAuthHeaders(accessToken);

        // Create workspace
        var wsBody = Map.of("name", "Task Audit WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Task Audit Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create milestone
        authHeaders = buildAuthHeaders(accessToken);
        var msBody = Map.of("title", "Task Audit Milestone");
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
        var body = Map.of("title", "Audit Test Task");
        ResponseEntity<Map> response = restTemplate.exchange(
                taskBasePath(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── TASK_COMPLETED audit ──────────────────────────────────────────────

    @Nested
    @DisplayName("TASK_COMPLETED audit event")
    class TaskCompletedAudit {

        @Test
        @DisplayName("should write TASK_COMPLETED audit event")
        void shouldWriteTaskCompletedAuditEvent() {
            String taskId = createTaskViaApi();
            UUID taskUuid = UUID.fromString(taskId);

            // Complete the task
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Verify audit event
            List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT * FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_COMPLETED'",
                    taskId);
            assertThat(events).hasSize(1);

            Map<String, Object> event = events.get(0);
            assertThat(event.get("actor_id")).isEqualTo(userId);
            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("TASK");
            assertThat(event.get("target_id")).isEqualTo(taskUuid);
            assertThat(event.get("workspace_id")).isEqualTo(UUID.fromString(workspaceId));
        }

        @Test
        @DisplayName("metadata should contain status transition")
        void metadataShouldContainStatusTransition() {
            String taskId = createTaskViaApi();

            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_COMPLETED'",
                    String.class, taskId);

            assertThat(metadata).contains("previousStatus");
            assertThat(metadata).contains("OPEN");
            assertThat(metadata).contains("newStatus");
            assertThat(metadata).contains("COMPLETED");
        }

        @Test
        @DisplayName("metadata should not contain sensitive data")
        void metadataShouldNotContainSensitiveData() {
            String taskId = createTaskViaApi();

            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_COMPLETED'",
                    String.class, taskId);

            assertThat(metadata).doesNotContain("password");
            assertThat(metadata).doesNotContain("token");
            assertThat(metadata).doesNotContain("secret");
            assertThat(metadata).doesNotContain("cookie");
        }
    }

    // ── TASK_REOPENED audit ───────────────────────────────────────────────

    @Nested
    @DisplayName("TASK_REOPENED audit event")
    class TaskReopenedAudit {

        @Test
        @DisplayName("should write TASK_REOPENED audit event")
        void shouldWriteTaskReopenedAuditEvent() {
            String taskId = createTaskViaApi();
            UUID taskUuid = UUID.fromString(taskId);

            // Complete then reopen
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            // Verify audit event
            List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT * FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_REOPENED'",
                    taskId);
            assertThat(events).hasSize(1);

            Map<String, Object> event = events.get(0);
            assertThat(event.get("actor_id")).isEqualTo(userId);
            assertThat(event.get("actor_type")).isEqualTo("USER");
            assertThat(event.get("source")).isEqualTo("API");
            assertThat(event.get("target_type")).isEqualTo("TASK");
            assertThat(event.get("target_id")).isEqualTo(taskUuid);
            assertThat(event.get("workspace_id")).isEqualTo(UUID.fromString(workspaceId));
        }

        @Test
        @DisplayName("metadata should contain status transition")
        void metadataShouldContainStatusTransition() {
            String taskId = createTaskViaApi();

            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_REOPENED'",
                    String.class, taskId);

            assertThat(metadata).contains("previousStatus");
            assertThat(metadata).contains("COMPLETED");
            assertThat(metadata).contains("newStatus");
            assertThat(metadata).contains("OPEN");
        }

        @Test
        @DisplayName("metadata should not contain sensitive data")
        void metadataShouldNotContainSensitiveData() {
            String taskId = createTaskViaApi();

            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);
            restTemplate.exchange(
                    taskBasePath() + "/" + taskId + "/reopen", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Map.class);

            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE target_id = ?::uuid AND event_type = 'TASK_REOPENED'",
                    String.class, taskId);

            assertThat(metadata).doesNotContain("password");
            assertThat(metadata).doesNotContain("token");
            assertThat(metadata).doesNotContain("secret");
            assertThat(metadata).doesNotContain("cookie");
        }
    }
}
