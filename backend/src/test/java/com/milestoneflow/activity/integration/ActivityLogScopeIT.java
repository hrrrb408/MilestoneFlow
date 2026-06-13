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
 * Scope isolation tests for activity log endpoints.
 *
 * <p>Creates two separate workspaces with their own projects, milestones,
 * and tasks, then verifies that activity timelines are strictly scoped
 * and never leak cross-workspace data.
 */
@DisplayName("Activity Log Scope IT")
class ActivityLogScopeIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String USER_A_EMAIL = "activity-scope-a@example.com";
    private static final String USER_B_EMAIL = "activity-scope-b@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_A_SLUG = "activity-scope-ws-a";
    private static final String WS_B_SLUG = "activity-scope-ws-b";

    private String userAToken;
    private String userBToken;
    private HttpHeaders readHeadersA;
    private HttpHeaders readHeadersB;

    private String workspaceAId;
    private String projectAId;
    private String milestoneAId;
    private String taskAId;

    private String workspaceBId;
    private String projectBId;
    private String milestoneBId;
    private String taskBId;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);

        // Create users A and B
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, USER_A_EMAIL, USER_A_EMAIL.toLowerCase(), "User A", encodedPassword);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, USER_B_EMAIL, USER_B_EMAIL.toLowerCase(), "User B", encodedPassword);

        userAToken = loginAndGetToken(USER_A_EMAIL);
        userBToken = loginAndGetToken(USER_B_EMAIL);
        readHeadersA = buildReadHeaders(userAToken);
        readHeadersB = buildReadHeaders(userBToken);

        // Create Workspace A with full hierarchy
        var authA = buildAuthHeaders(userAToken);
        var wsBodyA = Map.of("name", "Workspace A", "slug", WS_A_SLUG);
        ResponseEntity<Map> wsAResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBodyA, authA), Map.class);
        @SuppressWarnings("unchecked")
        var wsAData = (Map<String, Object>) wsAResponse.getBody().get("data");
        workspaceAId = (String) wsAData.get("id");

        authA = buildAuthHeaders(userAToken);
        var projBodyA = Map.of("name", "Project A");
        ResponseEntity<Map> projAResponse = restTemplate.exchange(
                "/workspaces/" + workspaceAId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBodyA, authA), Map.class);
        @SuppressWarnings("unchecked")
        var projAData = (Map<String, Object>) projAResponse.getBody().get("data");
        projectAId = (String) projAData.get("id");

        authA = buildAuthHeaders(userAToken);
        var msBodyA = Map.of("title", "Milestone A");
        ResponseEntity<Map> msAResponse = restTemplate.exchange(
                "/workspaces/" + workspaceAId + "/projects/" + projectAId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBodyA, authA), Map.class);
        @SuppressWarnings("unchecked")
        var msAData = (Map<String, Object>) msAResponse.getBody().get("data");
        milestoneAId = (String) msAData.get("id");

        authA = buildAuthHeaders(userAToken);
        var taskBodyA = Map.of("title", "Task A");
        ResponseEntity<Map> taskAResponse = restTemplate.exchange(
                "/workspaces/" + workspaceAId + "/projects/" + projectAId
                        + "/milestones/" + milestoneAId + "/tasks",
                HttpMethod.POST, new HttpEntity<>(taskBodyA, authA), Map.class);
        @SuppressWarnings("unchecked")
        var taskAData = (Map<String, Object>) taskAResponse.getBody().get("data");
        taskAId = (String) taskAData.get("id");

        // Create Workspace B with full hierarchy
        var authB = buildAuthHeaders(userBToken);
        var wsBodyB = Map.of("name", "Workspace B", "slug", WS_B_SLUG);
        ResponseEntity<Map> wsBResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBodyB, authB), Map.class);
        @SuppressWarnings("unchecked")
        var wsBData = (Map<String, Object>) wsBResponse.getBody().get("data");
        workspaceBId = (String) wsBData.get("id");

        authB = buildAuthHeaders(userBToken);
        var projBodyB = Map.of("name", "Project B");
        ResponseEntity<Map> projBResponse = restTemplate.exchange(
                "/workspaces/" + workspaceBId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBodyB, authB), Map.class);
        @SuppressWarnings("unchecked")
        var projBData = (Map<String, Object>) projBResponse.getBody().get("data");
        projectBId = (String) projBData.get("id");

        authB = buildAuthHeaders(userBToken);
        var msBodyB = Map.of("title", "Milestone B");
        ResponseEntity<Map> msBResponse = restTemplate.exchange(
                "/workspaces/" + workspaceBId + "/projects/" + projectBId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBodyB, authB), Map.class);
        @SuppressWarnings("unchecked")
        var msBData = (Map<String, Object>) msBResponse.getBody().get("data");
        milestoneBId = (String) msBData.get("id");

        authB = buildAuthHeaders(userBToken);
        var taskBodyB = Map.of("title", "Task B");
        ResponseEntity<Map> taskBResponse = restTemplate.exchange(
                "/workspaces/" + workspaceBId + "/projects/" + projectBId
                        + "/milestones/" + milestoneBId + "/tasks",
                HttpMethod.POST, new HttpEntity<>(taskBodyB, authB), Map.class);
        @SuppressWarnings("unchecked")
        var taskBData = (Map<String, Object>) taskBResponse.getBody().get("data");
        taskBId = (String) taskBData.get("id");
    }

    private void cleanAll() {
        String normA = USER_A_EMAIL.toLowerCase();
        String normB = USER_B_EMAIL.toLowerCase();
        for (String slug : List.of(WS_A_SLUG, WS_B_SLUG)) {
            jdbc.update("DELETE FROM task WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", slug);
            jdbc.update("DELETE FROM milestone WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", slug);
            jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", slug);
            jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM workspace WHERE slug = ?)", slug);
        }
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", normA, normB);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", normA, normB);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        for (String slug : List.of(WS_A_SLUG, WS_B_SLUG)) {
            jdbc.update("DELETE FROM workspace WHERE slug = ?", slug);
        }
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", normA, normB);
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", normA, normB);
        jdbc.update("DELETE FROM app_user WHERE email_normalized IN (?, ?)", normA, normB);
    }

    private String loginAndGetToken(String email) {
        var body = Map.of("email", email, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        return extractCookie(response, "MF_ACCESS=");
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

    // ── Workspace scope isolation ────────────────────────────────────────────

    @Nested
    @DisplayName("Workspace scope isolation")
    class WorkspaceScopeIsolation {

        @Test
        @DisplayName("Workspace A timeline should not contain Workspace B events")
        void workspaceAShouldNotContainBEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // All items must belong to workspace A
            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> workspaceAId.equals(i.get("workspaceId")))).isTrue();

            // Should not contain Workspace B project/task events
            var targetIds = items.stream().map(i -> (String) i.get("targetId")).toList();
            assertThat(targetIds).doesNotContain(projectBId, milestoneBId, taskBId);
        }

        @Test
        @DisplayName("Workspace B timeline should not contain Workspace A events")
        void workspaceBShouldNotContainAEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceBId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersB), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            var targetIds = items.stream().map(i -> (String) i.get("targetId")).toList();
            assertThat(targetIds).doesNotContain(projectAId, milestoneAId, taskAId);
        }
    }

    // ── Project scope isolation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Project scope isolation")
    class ProjectScopeIsolation {

        @Test
        @DisplayName("Project A timeline should only contain Project A events")
        void projectAShouldOnlyContainAEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/projects/" + projectAId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // All items must target project A
            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> projectAId.equals(i.get("targetId")))).isTrue();
        }

        @Test
        @DisplayName("Project A timeline should not contain Project B events")
        void projectAShouldNotContainBEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/projects/" + projectAId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            var targetIds = items.stream().map(i -> (String) i.get("targetId")).toList();
            assertThat(targetIds).doesNotContain(projectBId);
        }
    }

    // ── Task scope isolation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Task scope isolation")
    class TaskScopeIsolation {

        @Test
        @DisplayName("Task A timeline should only contain Task A events")
        void taskAShouldOnlyContainAEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/projects/" + projectAId
                            + "/milestones/" + milestoneAId + "/tasks/" + taskAId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            assertThat(items).isNotEmpty();
            assertThat(items.stream().allMatch(i -> taskAId.equals(i.get("targetId")))).isTrue();
        }

        @Test
        @DisplayName("Task A timeline should not contain Task B events")
        void taskAShouldNotContainBEvents() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/projects/" + projectAId
                            + "/milestones/" + milestoneAId + "/tasks/" + taskAId + "/activities",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            var targetIds = items.stream().map(i -> (String) i.get("targetId")).toList();
            assertThat(targetIds).doesNotContain(taskBId);
        }
    }

    // ── Target type filter isolation ─────────────────────────────────────────

    @Nested
    @DisplayName("Filter isolation")
    class FilterIsolation {

        @Test
        @DisplayName("targetType filter should not leak cross-workspace data")
        void targetTypeFilterShouldNotLeak() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/activities?targetType=TASK",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // All items must be from workspace A
            assertThat(items.stream().allMatch(i -> workspaceAId.equals(i.get("workspaceId")))).isTrue();
            // And all must be TASK type
            assertThat(items.stream().allMatch(i -> "TASK".equals(i.get("targetType")))).isTrue();
        }

        @Test
        @DisplayName("eventType filter should not leak cross-workspace data")
        void eventTypeFilterShouldNotLeak() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceAId + "/activities?eventType=TASK_CREATED",
                    HttpMethod.GET, new HttpEntity<>(readHeadersA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (List<Map<String, Object>>) data.get("items");

            // All items must be from workspace A
            assertThat(items.stream().allMatch(i -> workspaceAId.equals(i.get("workspaceId")))).isTrue();
            // And all must be TASK_CREATED events
            assertThat(items.stream().allMatch(i -> "TASK_CREATED".equals(i.get("eventType")))).isTrue();
        }
    }
}
