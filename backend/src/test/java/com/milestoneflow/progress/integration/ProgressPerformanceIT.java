package com.milestoneflow.progress.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance / scale verification for the progress read model.
 *
 * <p>Provisions a non-trivial workload (1 project, 20 milestones, 200 tasks)
 * via direct JDBC and asserts that the progress APIs return correct aggregates.
 *
 * <p>No strict millisecond threshold is asserted — CI timing is too noisy for
 * that. Instead this test guards against correctness regressions at scale and
 * documents (via the asserted result shape) that both the project progress and
 * the milestone progress list resolve with single aggregate SQL statements:
 * <ul>
 *   <li>Project progress = one {@code COUNT(*) FILTER(...)} scan over tasks +
 *       one over milestones (see {@code ProgressQueryRepositoryAdapter}).</li>
 *   <li>Milestone progress list = one {@code LEFT JOIN} + conditional
 *       aggregate grouped per milestone — explicitly NOT an N+1 of one query
 *       per milestone.</li>
 * </ul>
 * The elapsed time for each call is printed to stdout for observability.
 */
@DisplayName("Progress Performance IT")
class ProgressPerformanceIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "progress-perf-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "progress-perf-ws";

    private static final int MILESTONE_COUNT = 20;
    private static final int TASKS_PER_MILESTONE = 10;
    private static final int COMPLETED_PER_MILESTONE = 5;

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
            """, EMAIL, EMAIL.toLowerCase(), "Perf IT User", encodedPassword);

        bootstrapWorkspaceAndProject();
        provisionBulkData();

        readHeaders = buildReadHeaders(getAccessToken());
    }

    @Test
    @DisplayName("project progress aggregates 200 tasks correctly at scale")
    void projectProgressAtScale() {
        long start = System.nanoTime();
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/progress",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");

        int expectedTotal = MILESTONE_COUNT * TASKS_PER_MILESTONE;
        int expectedCompleted = MILESTONE_COUNT * COMPLETED_PER_MILESTONE;

        assertThat(data.get("totalTasks")).isEqualTo(expectedTotal);
        assertThat(data.get("completedTasks")).isEqualTo(expectedCompleted);
        assertThat(data.get("openTasks")).isEqualTo(expectedTotal - expectedCompleted);
        assertThat(data.get("completionRate")).isEqualTo(50.00);
        assertThat(data.get("totalMilestones")).isEqualTo(MILESTONE_COUNT);
        assertThat(data.get("completedMilestones")).isEqualTo(0);
        assertThat(data.get("openMilestones")).isEqualTo(MILESTONE_COUNT);

        System.out.printf("[ProgressPerformanceIT] project progress over %d tasks: %d ms%n",
                expectedTotal, elapsedMs);
    }

    @Test
    @DisplayName("milestone progress list resolves all 20 milestones without N+1 (single aggregate)")
    void milestoneProgressListAtScale() {
        long start = System.nanoTime();
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones/progress",
                HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        assertThat(items).hasSize(MILESTONE_COUNT);

        // Every milestone carries the same 10/5 distribution; sum must match the project total.
        int totalTasksSum = items.stream()
                .mapToInt(i -> ((Number) i.get("totalTasks")).intValue()).sum();
        int completedTasksSum = items.stream()
                .mapToInt(i -> ((Number) i.get("completedTasks")).intValue()).sum();
        assertThat(totalTasksSum).isEqualTo(MILESTONE_COUNT * TASKS_PER_MILESTONE);
        assertThat(completedTasksSum).isEqualTo(MILESTONE_COUNT * COMPLETED_PER_MILESTONE);

        // Each entry is independently task-aggregated.
        items.forEach(i -> {
            assertThat(i.get("totalTasks")).isEqualTo(TASKS_PER_MILESTONE);
            assertThat(i.get("completedTasks")).isEqualTo(COMPLETED_PER_MILESTONE);
            assertThat(i.get("completionRate")).isEqualTo(50.00);
        });

        System.out.printf("[ProgressPerformanceIT] milestone list over %d milestones: %d ms%n",
                MILESTONE_COUNT, elapsedMs);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Inserts 20 milestones (even-indexed with an ascending due date, odd-indexed
     * with none) and 10 tasks per milestone (5 COMPLETED, 5 OPEN) directly via
     * JDBC for speed. The mixed due dates exercise the list query's NULLS LAST
     * ordering without coupling the assertions to a specific order.
     */
    private void provisionBulkData() {
        UUID ws = UUID.fromString(workspaceId);
        UUID proj = UUID.fromString(projectId);

        List<Object[]> milestoneBatch = new ArrayList<>();
        List<UUID> milestoneIds = new ArrayList<>();
        java.time.LocalDate baseDate = java.time.LocalDate.of(2026, 7, 1);
        for (int m = 0; m < MILESTONE_COUNT; m++) {
            UUID milestoneId = UUID.randomUUID();
            milestoneIds.add(milestoneId);
            // Even-indexed milestones carry a due date; odd-indexed leave it NULL.
            Object dueDate = (m % 2 == 0) ? baseDate.plusDays(m / 2L) : null;
            milestoneBatch.add(new Object[]{milestoneId, ws, proj, "Milestone " + m, dueDate});
        }
        jdbc.batchUpdate(
                "INSERT INTO milestone (id, workspace_id, project_id, title, due_date) "
                        + "VALUES (?, ?, ?, ?, ?)",
                milestoneBatch);

        List<Object[]> taskBatch = new ArrayList<>();
        for (UUID milestoneId : milestoneIds) {
            for (int t = 0; t < TASKS_PER_MILESTONE; t++) {
                String status = t < COMPLETED_PER_MILESTONE ? "COMPLETED" : "OPEN";
                taskBatch.add(new Object[]{
                        UUID.randomUUID(), ws, proj, milestoneId, "Task " + t, status});
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                taskBatch);
    }

    private void bootstrapWorkspaceAndProject() {
        String token = getAccessToken();
        ResponseEntity<Map> wsResp = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Perf WS", "slug", WS_SLUG),
                        buildAuthHeaders(token)), Map.class);
        assertThat(wsResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResp.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        ResponseEntity<Map> projResp = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Perf Project"), buildAuthHeaders(token)), Map.class);
        assertThat(projResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResp.getBody().get("data");
        projectId = (String) projData.get("id");
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
