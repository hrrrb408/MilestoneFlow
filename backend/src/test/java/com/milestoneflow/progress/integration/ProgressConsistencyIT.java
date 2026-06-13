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
 * Consistency integration tests for the progress read model.
 *
 * <p>Verifies that progress is always derived from task aggregation regardless
 * of milestone/project status, and that inconsistent source states are surfaced
 * accurately rather than masked:
 * <ul>
 *   <li>A milestone marked COMPLETED with open tasks reports its real task rate.</li>
 *   <li>A milestone still OPEN whose tasks are all done reports 100.00.</li>
 *   <li>An archived project remains readable and reports correct counts.</li>
 *   <li>Project progress is task-aggregated, never the average of milestone rates.</li>
 * </ul>
 *
 * <p>This is a read-only model: it reflects data as-is and does not "repair"
 * status inconsistencies (per B6-002 scope).
 */
@DisplayName("Progress Consistency IT")
class ProgressConsistencyIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "progress-consistency-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "progress-consistency-ws";

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
            """, EMAIL, EMAIL.toLowerCase(), "Consistency IT User", encodedPassword);

        workspaceId = bootstrapWorkspaceAndProject();
        readHeaders = buildReadHeaders(getAccessToken());
    }

    // ── Milestone status vs. progress ─────────────────────────────────────

    @Nested
    @DisplayName("Milestone status does not drive completion rate")
    class MilestoneStatusIndependence {

        @Test
        @DisplayName("COMPLETED milestone with open tasks reports task-based rate, not 100.00")
        void completedMilestoneWithOpenTasks() {
            String ms = createMilestone("Inconsistent Milestone");
            milestoneWithTasks(ms, 3, 2); // 2 of 3 completed
            completeMilestone(ms);        // mark COMPLETED despite open task

            Map<String, Object> data = milestoneProgress(ms);

            assertThat(data.get("milestoneStatus")).isEqualTo("COMPLETED");
            assertThat(data.get("totalTasks")).isEqualTo(3);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("openTasks")).isEqualTo(1);
            // Task-based rate is 66.67, NOT 100.00 despite COMPLETED status.
            assertThat(data.get("completionRate")).isEqualTo(66.67);
        }

        @Test
        @DisplayName("OPEN milestone whose tasks are all completed reports 100.00")
        void openMilestoneAllTasksCompleted() {
            String ms = createMilestone("Done But Open");
            milestoneWithTasks(ms, 2, 2); // all completed, milestone stays OPEN

            Map<String, Object> data = milestoneProgress(ms);

            assertThat(data.get("milestoneStatus")).isEqualTo("OPEN");
            assertThat(data.get("totalTasks")).isEqualTo(2);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("completionRate")).isEqualTo(100.00);
        }
    }

    // ── Project status vs. progress ───────────────────────────────────────

    @Nested
    @DisplayName("Project status does not block or skew progress")
    class ProjectStatusIndependence {

        @Test
        @DisplayName("ARCHIVED project with partial tasks remains readable with correct counts")
        void archivedProjectRemainsReadable() {
            String ms = createMilestone("Archive Milestone");
            milestoneWithTasks(ms, 4, 1); // 1 of 4 completed → 25.00

            archiveProject();

            Map<String, Object> data = projectProgress();
            assertThat(data.get("totalTasks")).isEqualTo(4);
            assertThat(data.get("completedTasks")).isEqualTo(1);
            assertThat(data.get("openTasks")).isEqualTo(3);
            assertThat(data.get("completionRate")).isEqualTo(25.00);
        }

        @Test
        @DisplayName("project progress is task-aggregated, not the average of milestone rates")
        void projectUsesTaskAggregationNotMilestoneAverage() {
            // Milestone A: marked COMPLETED but only 2/3 tasks done → task rate 66.67
            String a = createMilestone("A");
            milestoneWithTasks(a, 3, 2);
            completeMilestone(a);

            // Milestone B: 0/2 tasks done → task rate 0.00
            createMilestone("B");
            milestoneWithTasks(lastMilestoneId(), 2, 0);

            // Milestone-rate average would be (66.67 + 0.00) / 2 = 33.33.
            // Task aggregation is 2 completed / 5 total = 40.00.
            Map<String, Object> data = projectProgress();
            assertThat(data.get("totalTasks")).isEqualTo(5);
            assertThat(data.get("completedTasks")).isEqualTo(2);
            assertThat(data.get("completionRate")).isEqualTo(40.00);

            // And the completed milestone count reflects status, not task rates.
            assertThat(data.get("totalMilestones")).isEqualTo(2);
            assertThat(data.get("completedMilestones")).isEqualTo(1);
            assertThat(data.get("openMilestones")).isEqualTo(1);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String lastMilestoneIdCache;

    private String lastMilestoneId() {
        return lastMilestoneIdCache;
    }

    private String createMilestone(String title) {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(Map.of("title", title), auth), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().get("data");
        lastMilestoneIdCache = (String) data.get("id");
        return lastMilestoneIdCache;
    }

    private void milestoneWithTasks(String milestoneId, int total, int completed) {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        for (int i = 0; i < total; i++) {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/tasks",
                    HttpMethod.POST, new HttpEntity<>(Map.of("title", "Task " + i), auth), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        for (int i = 0; i < completed; i++) {
            String taskId = getTaskId(milestoneId, "Task " + i);
            auth = buildAuthHeaders(getAccessToken());
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId
                            + "/milestones/" + milestoneId + "/tasks/" + taskId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(auth), Map.class);
        }
    }

    private void completeMilestone(String milestoneId) {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/milestones/" + milestoneId + "/complete",
                HttpMethod.POST, new HttpEntity<>(auth), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void archiveProject() {
        HttpHeaders auth = buildAuthHeaders(getAccessToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                HttpMethod.POST, new HttpEntity<>(auth), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private String bootstrapWorkspaceAndProject() {
        String token = getAccessToken();
        ResponseEntity<Map> wsResp = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Consistency WS", "slug", WS_SLUG),
                        buildAuthHeaders(token)), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResp.getBody().get("data");
        String wsId = (String) wsData.get("id");

        ResponseEntity<Map> projResp = restTemplate.exchange(
                "/workspaces/" + wsId + "/projects", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Consistency Project"),
                        buildAuthHeaders(token)), Map.class);
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
