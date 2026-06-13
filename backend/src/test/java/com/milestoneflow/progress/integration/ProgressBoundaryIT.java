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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary integration tests for the progress read model.
 *
 * <p>Pins the exact completion-rate values produced by task aggregation across
 * the full set of ratios required by the B6-002 spec (0/0, 1/1, 1/2, 1/3, 2/3,
 * 1/6, 5/6) and the zero-task / zero-milestone boundaries. These tests are
 * intentionally dedicated and exhaustive so that rounding or zero-rule
 * regressions surface independently of the broader flow tests.
 */
@DisplayName("Progress Boundary IT")
class ProgressBoundaryIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "progress-boundary-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "progress-boundary-ws";

    private String workspaceId;
    private String projectId;
    private HttpHeaders readHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Boundary IT User", encodedPassword);

        workspaceId = bootstrapWorkspaceAndProject();

        readHeaders = buildReadHeaders(getAccessToken());
    }

    // ── Milestone completion-rate boundaries ───────────────────────────────

    @Nested
    @DisplayName("Milestone completion rate boundaries")
    class MilestoneRateBoundaries {

        @Test
        @DisplayName("milestone with no tasks → 0.00")
        void noTasksReturnsZero() {
            String ms = createMilestone("Boundary Empty");

            Map<String, Object> data = milestoneProgress(ms);

            assertThat(data.get("totalTasks")).isEqualTo(0);
            assertThat(data.get("completedTasks")).isEqualTo(0);
            assertThat(data.get("openTasks")).isEqualTo(0);
            assertThat(data.get("completionRate")).isEqualTo(0.00);
        }

        @Test
        @DisplayName("1/1 completed → 100.00")
        void oneOfOne() {
            String ms = milestoneWithTasks(1, 1);
            Map<String, Object> data = milestoneProgress(ms);
            assertThat(data.get("completionRate")).isEqualTo(100.00);
            assertThat(data.get("totalTasks")).isEqualTo(1);
            assertThat(data.get("completedTasks")).isEqualTo(1);
            assertThat(data.get("openTasks")).isEqualTo(0);
        }

        @Test
        @DisplayName("1/2 completed → 50.00")
        void oneOfTwo() {
            String ms = milestoneWithTasks(2, 1);
            assertThat(milestoneProgress(ms).get("completionRate")).isEqualTo(50.00);
        }

        @Test
        @DisplayName("1/3 completed → 33.33")
        void oneOfThree() {
            String ms = milestoneWithTasks(3, 1);
            assertThat(milestoneProgress(ms).get("completionRate")).isEqualTo(33.33);
        }

        @Test
        @DisplayName("2/3 completed → 66.67")
        void twoOfThree() {
            String ms = milestoneWithTasks(3, 2);
            assertThat(milestoneProgress(ms).get("completionRate")).isEqualTo(66.67);
        }

        @Test
        @DisplayName("1/6 completed → 16.67")
        void oneOfSix() {
            String ms = milestoneWithTasks(6, 1);
            assertThat(milestoneProgress(ms).get("completionRate")).isEqualTo(16.67);
        }

        @Test
        @DisplayName("5/6 completed → 83.33")
        void fiveOfSix() {
            String ms = milestoneWithTasks(6, 5);
            assertThat(milestoneProgress(ms).get("completionRate")).isEqualTo(83.33);
        }

        @Test
        @DisplayName("openTasks + completedTasks equals totalTasks")
        void taskCountsAreConsistent() {
            String ms = milestoneWithTasks(6, 5);
            Map<String, Object> data = milestoneProgress(ms);
            int total = ((Number) data.get("totalTasks")).intValue();
            int completed = ((Number) data.get("completedTasks")).intValue();
            int open = ((Number) data.get("openTasks")).intValue();
            assertThat(completed + open).isEqualTo(total);
        }
    }

    // ── Project completion-rate boundaries ─────────────────────────────────

    @Nested
    @DisplayName("Project completion rate boundaries")
    class ProjectRateBoundaries {

        @Test
        @DisplayName("project with milestones but no tasks → 0.00")
        void milestonesButNoTasks() {
            createMilestone("M1");
            createMilestone("M2");

            Map<String, Object> data = projectProgress();

            assertThat(data.get("totalTasks")).isEqualTo(0);
            assertThat(data.get("completionRate")).isEqualTo(0.00);
            assertThat(data.get("totalMilestones")).isEqualTo(2);
            assertThat(data.get("completedMilestones")).isEqualTo(0);
            assertThat(data.get("openMilestones")).isEqualTo(2);
        }

        @Test
        @DisplayName("project with no milestones and no tasks → 0.00")
        void noMilestonesAndNoTasks() {
            Map<String, Object> data = projectProgress();
            assertThat(data.get("totalMilestones")).isEqualTo(0);
            assertThat(data.get("totalTasks")).isEqualTo(0);
            assertThat(data.get("completionRate")).isEqualTo(0.00);
        }

        @Test
        @DisplayName("project aggregates tasks across milestones at exact ratio")
        void projectAggregatesAtExactRatio() {
            // Milestone A: 3 tasks, 1 completed
            milestoneWithTasks(createMilestone("A"), 3, 1);
            // Milestone B: 3 tasks, 1 completed
            milestoneWithTasks(createMilestone("B"), 3, 1);
            // Project: 6 tasks, 2 completed → 33.33
            Map<String, Object> data = projectProgress();
            assertThat(data.get("totalTasks")).isEqualTo(6);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("openTasks")).isEqualTo(4);
            assertThat(data.get("completionRate")).isEqualTo(33.33);
        }

        @Test
        @DisplayName("milestone progress list returns 0.00 for zero-task milestones")
        void listHasZeroRateForEmptyMilestone() {
            String empty = createMilestone("Empty");
            milestoneWithTasks(createMilestone("WithTasks"), 2, 1);

            java.util.List<Map<String, Object>> items = milestoneProgressList();
            Map<String, Object> emptyEntry = items.stream()
                    .filter(i -> empty.equals(i.get("milestoneId")))
                    .findFirst().orElseThrow();
            assertThat(emptyEntry.get("totalTasks")).isEqualTo(0);
            assertThat(emptyEntry.get("completionRate")).isEqualTo(0.00);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Creates a milestone, creates {@code total} tasks under it, and completes
     * the first {@code completed} of them. Returns the milestone id.
     */
    private String milestoneWithTasks(int total, int completed) {
        return milestoneWithTasks(createMilestone("M-" + total + "-" + completed), total, completed);
    }

    private String milestoneWithTasks(String milestoneId, int total, int completed) {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        for (int i = 0; i < total; i++) {
            var body = Map.of("title", "Task " + i);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/tasks",
                    HttpMethod.POST, new HttpEntity<>(body, auth), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        // Complete the first `completed` tasks
        for (int i = 0; i < completed; i++) {
            String taskId = getTaskId(milestoneId, "Task " + i);
            auth = buildAuthHeaders(getAccessToken());
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/tasks/" + taskId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(auth), Map.class);
        }
        return milestoneId;
    }

    private String createMilestone(String title) {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(Map.of("title", title), auth), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("id");
    }

    private String getTaskId(String msId, String title) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + msId + "/tasks",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().get("data");
        @SuppressWarnings("unchecked")
        var items = (java.util.List<Map<String, Object>>) data.get("items");
        return items.stream()
                .filter(t -> title.equals(t.get("title")))
                .map(t -> (String) t.get("id"))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> milestoneProgress(String milestoneId) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + milestoneId + "/progress",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectProgress() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/progress",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> milestoneProgressList() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones/progress",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) resp.getBody().get("data");
        return (java.util.List<Map<String, Object>>) data.get("items");
    }

    private String bootstrapWorkspaceAndProject() {
        String token = getAccessToken();
        var wsBody = Map.of("name", "Boundary WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResp = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, buildAuthHeaders(token)), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResp.getBody().get("data");
        String wsId = (String) wsData.get("id");

        var projBody = Map.of("name", "Boundary Project");
        ResponseEntity<Map> projResp = restTemplate.exchange(
                "/workspaces/" + wsId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, buildAuthHeaders(token)), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResp.getBody().get("data");
        projectId = (String) projData.get("id");
        return wsId;
    }

    private String getAccessToken() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return extractCookie(resp, "MF_ACCESS=");
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
        ResponseEntity<Void> csrfResp = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(csrfHeaders), Void.class);
        var setCookies = csrfResp.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            String xsrf = setCookies.stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElse(null);
            if (xsrf != null) {
                String csrfToken = xsrf.split("XSRF-TOKEN=")[1].split(";")[0];
                headers.add("X-XSRF-TOKEN", csrfToken);
                headers.add("Cookie", xsrf.split(";")[0]);
            }
        }
        return headers;
    }

    private HttpHeaders buildReadHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessToken);
        return headers;
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
}
