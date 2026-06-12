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
 * Integration tests for milestone complete/reopen status flow.
 *
 * <p>Verifies the full lifecycle: OPEN → COMPLETED → OPEN,
 * including completedAt/completedBy behaviour, update restrictions,
 * archived project restrictions, and list status filtering.
 */
@DisplayName("Milestone Status Flow IT")
class MilestoneStatusFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "ms-status-flow@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "ms-status-flow-ws";

    private String workspaceId;
    private String projectId;
    private HttpHeaders authHeaders;
    private HttpHeaders authHeadersOnly;

    @BeforeEach
    void setUp() {
        cleanAll();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Status Flow IT User", encodedPassword);

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

        var wsBody = Map.of("name", "Status Flow WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        authHeaders = buildAuthHeaders(accessToken);
        var projBody = Map.of("name", "Status Flow Project");
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

    private String createMilestone(String title) {
        var body = Map.of("title", title);
        ResponseEntity<Map> response = restTemplate.exchange(
                milestoneBasePath(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── POST /milestones/{id}/complete ────────────────────────────────────

    @Nested
    @DisplayName("POST /milestones/{id}/complete")
    class CompleteMilestone {

        @Test
        @DisplayName("should complete an OPEN milestone with 200")
        void shouldCompleteOpenMilestone() {
            String milestoneId = createMilestone("Complete Me");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("COMPLETED");
            assertThat(data.get("completedAt")).isNotNull();
        }

        @Test
        @DisplayName("should set completedAt in database")
        void shouldSetCompletedAtInDatabase() {
            String milestoneId = createMilestone("DB Complete");

            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, completed_at, completed_by FROM milestone WHERE id = ?::uuid",
                    milestoneId);
            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("completed_at")).isNotNull();
            assertThat(row.get("completed_by")).isNotNull();
        }

        @Test
        @DisplayName("should return 409 when completing an already COMPLETED milestone")
        void shouldRejectDoubleComplete() {
            String milestoneId = createMilestone("Double Complete");

            // First complete succeeds
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Second complete fails
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("MILESTONE_ALREADY_COMPLETED");
        }

        @Test
        @DisplayName("should return 404 for non-existent milestone complete")
        void shouldReturn404ForNonExistentComplete() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + randomId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 when completing milestone in archived project")
        void shouldRejectCompleteInArchivedProject() {
            String milestoneId = createMilestone("Archived Complete");

            // Archive the project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Try to complete
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_ARCHIVED");
        }
    }

    // ── POST /milestones/{id}/reopen ──────────────────────────────────────

    @Nested
    @DisplayName("POST /milestones/{id}/reopen")
    class ReopenMilestone {

        @Test
        @DisplayName("should reopen a COMPLETED milestone with 200")
        void shouldReopenCompletedMilestone() {
            String milestoneId = createMilestone("Reopen Me");

            // Complete first
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Reopen
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("OPEN");
            assertThat(data.get("completedAt")).isNull();
        }

        @Test
        @DisplayName("should clear completedAt and completedBy in database")
        void shouldClearCompletedAtInDatabase() {
            String milestoneId = createMilestone("DB Reopen");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Reopen
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, completed_at, completed_by FROM milestone WHERE id = ?::uuid",
                    milestoneId);
            assertThat(row.get("status")).isEqualTo("OPEN");
            assertThat(row.get("completed_at")).isNull();
            assertThat(row.get("completed_by")).isNull();
        }

        @Test
        @DisplayName("should return 409 when reopening an OPEN milestone")
        void shouldRejectReopenOpen() {
            String milestoneId = createMilestone("Already Open");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("MILESTONE_NOT_COMPLETED");
        }

        @Test
        @DisplayName("should return 404 for non-existent milestone reopen")
        void shouldReturn404ForNonExistentReopen() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + randomId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 when reopening milestone in archived project")
        void shouldRejectReopenInArchivedProject() {
            String milestoneId = createMilestone("Archived Reopen");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Archive the project
            restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Try to reopen
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("PROJECT_ARCHIVED");
        }
    }

    // ── COMPLETED milestone update restriction ─────────────────────────────

    @Nested
    @DisplayName("COMPLETED milestone update restriction")
    class CompletedUpdateRestriction {

        @Test
        @DisplayName("should reject PATCH on COMPLETED milestone with 409")
        void shouldRejectUpdateOnCompleted() {
            String milestoneId = createMilestone("Completed Update Test");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Try to update
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            var updateBody = Map.of("title", "Should Not Work");
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId,
                    HttpMethod.PATCH, new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("MILESTONE_COMPLETED");
        }

        @Test
        @DisplayName("should allow PATCH after reopen")
        void shouldAllowUpdateAfterReopen() {
            String milestoneId = createMilestone("Reopen Update Test");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Reopen
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Now update should succeed
            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            var updateBody = Map.of("title", "Updated After Reopen");
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId,
                    HttpMethod.PATCH, new HttpEntity<>(updateBody, authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Updated After Reopen");
        }
    }

    // ── List status filter after complete/reopen ───────────────────────────

    @Nested
    @DisplayName("List status filter after complete/reopen")
    class ListStatusFilter {

        @Test
        @DisplayName("should list both OPEN and COMPLETED by default")
        void shouldListBothByDefault() {
            String ms1 = createMilestone("Open One");
            String ms2 = createMilestone("To Complete");
            createMilestone("Open Two");

            // Complete ms2
            restTemplate.exchange(
                    milestoneBasePath() + "/" + ms2 + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(3);
        }

        @Test
        @DisplayName("should filter COMPLETED milestones after complete")
        void shouldFilterCompleted() {
            createMilestone("Stays Open");
            String ms2 = createMilestone("Will Complete");
            createMilestone("Also Open");

            // Complete ms2
            restTemplate.exchange(
                    milestoneBasePath() + "/" + ms2 + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=COMPLETED", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should filter OPEN milestones after reopen")
        void shouldFilterOpenAfterReopen() {
            String ms1 = createMilestone("Complete and Reopen");
            createMilestone("Always Open");

            // Complete then reopen
            restTemplate.exchange(
                    milestoneBasePath() + "/" + ms1 + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            authHeaders = buildAuthHeaders(extractCookie(
                    restTemplate.exchange("/auth/login", HttpMethod.POST,
                            new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                    new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                            Map.class), "MF_ACCESS="));

            restTemplate.exchange(
                    milestoneBasePath() + "/" + ms1 + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            // Filter OPEN
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=OPEN", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.stream().allMatch(i -> "OPEN".equals(i.get("status")))).isTrue();
        }

        @Test
        @DisplayName("should return 422 for invalid status value")
        void shouldRejectInvalidStatus() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=INVALID", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().get("code")).isEqualTo("MILESTONE_INVALID_STATUS");
        }

        @Test
        @DisplayName("should include completedAt in list response")
        void shouldIncludeCompletedAtInList() {
            String msId = createMilestone("With CompletedAt");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + msId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "?status=COMPLETED", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0).get("completedAt")).isNotNull();
        }
    }

    // ── COMPLETED milestone detail ─────────────────────────────────────────

    @Nested
    @DisplayName("COMPLETED milestone detail")
    class CompletedMilestoneDetail {

        @Test
        @DisplayName("should read COMPLETED milestone detail with completedAt")
        void shouldReadCompletedDetail() {
            String milestoneId = createMilestone("Detail Test");

            // Complete
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("status")).isEqualTo("COMPLETED");
            assertThat(data.get("completedAt")).isNotNull();
        }
    }

    // ── Cross workspace/project isolation for complete/reopen ─────────────

    @Nested
    @DisplayName("Cross workspace/project isolation for status operations")
    class CrossScopeIsolation {

        @Test
        @DisplayName("should return 404 when completing milestone with wrong workspace")
        void shouldReturn404WrongWorkspaceComplete() {
            String milestoneId = createMilestone("Cross WS");
            String fakeWorkspaceId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + fakeWorkspaceId + "/projects/" + projectId + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when completing milestone with wrong project")
        void shouldReturn404WrongProjectComplete() {
            String milestoneId = createMilestone("Cross Proj");
            String fakeProjectId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/projects/" + fakeProjectId + "/milestones/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when reopening milestone with wrong workspace")
        void shouldReturn404WrongWorkspaceReopen() {
            String milestoneId = createMilestone("Cross WS Reopen");

            // Complete first
            restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            String fakeWorkspaceId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + fakeWorkspaceId + "/projects/" + projectId + "/milestones/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
