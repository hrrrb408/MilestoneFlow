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
 * Security integration tests for task operations.
 *
 * <p>Verifies authentication, authorization, and data isolation boundaries.
 */
@DisplayName("Task Security IT")
class TaskSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String OWNER_EMAIL = "task-sec-owner@example.com";
    private static final String OTHER_EMAIL = "task-sec-other@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "task-sec-ws";

    private String ownerToken;
    private HttpHeaders ownerAuthHeaders;
    private HttpHeaders ownerReadHeaders;
    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private String taskId;

    @BeforeEach
    void setUp() {
        cleanAll();

        // Create owner user
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, OWNER_EMAIL, OWNER_EMAIL.toLowerCase(), "Owner User", encodedPassword);

        // Create other user
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, OTHER_EMAIL, OTHER_EMAIL.toLowerCase(), "Other User", encodedPassword);

        // Login owner
        ownerToken = loginAndGetToken(OWNER_EMAIL);

        // Create workspace
        var wsBody = Map.of("name", "Security WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, buildAuthHeaders(ownerToken)), Map.class);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Create project
        ownerAuthHeaders = buildAuthHeaders(ownerToken);
        var projBody = Map.of("name", "Security Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, ownerAuthHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Create milestone
        ownerAuthHeaders = buildAuthHeaders(ownerToken);
        var msBody = Map.of("title", "Security Milestone");
        ResponseEntity<Map> msResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBody, ownerAuthHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var msData = (Map<String, Object>) msResponse.getBody().get("data");
        milestoneId = (String) msData.get("id");

        // Create a task
        ownerAuthHeaders = buildAuthHeaders(ownerToken);
        ownerReadHeaders = buildReadHeaders(ownerToken);
        var taskBody = Map.of("title", "Security Test Task");
        ResponseEntity<Map> taskResponse = restTemplate.exchange(
                taskBasePath(), HttpMethod.POST,
                new HttpEntity<>(taskBody, ownerAuthHeaders), Map.class);
        @SuppressWarnings("unchecked")
        var taskData = (Map<String, Object>) taskResponse.getBody().get("data");
        taskId = (String) taskData.get("id");

        ownerAuthHeaders = buildAuthHeaders(ownerToken);
        ownerReadHeaders = buildReadHeaders(ownerToken);
    }

    private void cleanAll() {
        String ownerNorm = OWNER_EMAIL.toLowerCase();
        String otherNorm = OTHER_EMAIL.toLowerCase();
        jdbc.update("DELETE FROM task WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        jdbc.update("DELETE FROM milestone WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", ownerNorm, otherNorm);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", ownerNorm, otherNorm);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE slug = ?", WS_SLUG);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", ownerNorm, otherNorm);
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized IN (?, ?))", ownerNorm, otherNorm);
        jdbc.update("DELETE FROM app_user WHERE email_normalized IN (?, ?)", ownerNorm, otherNorm);
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

    private String taskBasePath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId
                + "/milestones/" + milestoneId + "/tasks";
    }

    // ── Anonymous access ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Anonymous access")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous POST with 401")
        void shouldRejectAnonymousPost() {
            var body = Map.of("title", "Test");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous GET with 401")
        void shouldRejectAnonymousGet() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous GET detail with 401")
        void shouldRejectAnonymousGetDetail() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous PATCH with 401")
        void shouldRejectAnonymousPatch() {
            var body = Map.of("title", "Updated");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── CSRF protection ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CSRF protection")
    class CsrfProtection {

        @Test
        @DisplayName("should reject POST without CSRF token")
        void shouldRejectPostWithoutCsrf() {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", ownerToken);

            var body = Map.of("title", "No CSRF");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).satisfiesAnyOf(
                    s -> assertThat(s).isEqualTo(HttpStatus.UNAUTHORIZED),
                    s -> assertThat(s).isEqualTo(HttpStatus.FORBIDDEN)
            );
        }

        @Test
        @DisplayName("should reject PATCH without CSRF token")
        void shouldRejectPatchWithoutCsrf() {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", ownerToken);

            var body = Map.of("title", "No CSRF");
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).satisfiesAnyOf(
                    s -> assertThat(s).isEqualTo(HttpStatus.UNAUTHORIZED),
                    s -> assertThat(s).isEqualTo(HttpStatus.FORBIDDEN)
            );
        }
    }

    // ── Non-member access ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-member access")
    class NonMemberAccess {

        private HttpHeaders otherReadHeaders;
        private HttpHeaders otherAuthHeaders;

        @BeforeEach
        void loginOther() {
            String otherToken = loginAndGetToken(OTHER_EMAIL);
            otherReadHeaders = buildReadHeaders(otherToken);
            otherAuthHeaders = buildAuthHeaders(otherToken);
        }

        @Test
        @DisplayName("should reject non-member GET list with 404")
        void shouldRejectNonMemberList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.GET,
                    new HttpEntity<>(otherReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should reject non-member GET detail with 404")
        void shouldRejectNonMemberGetDetail() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.GET,
                    new HttpEntity<>(otherReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should reject non-member POST with 404")
        void shouldRejectNonMemberCreate() {
            var body = Map.of("title", "Non-member Task");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, otherAuthHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should reject non-member PATCH with 404")
        void shouldRejectNonMemberUpdate() {
            var body = Map.of("title", "Non-member Update");

            ResponseEntity<Map> response = restTemplate.exchange(
                    taskBasePath() + "/" + taskId, HttpMethod.PATCH,
                    new HttpEntity<>(body, otherAuthHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Cross-workspace isolation ─────────────────────────────────────────

    @Nested
    @DisplayName("Cross-workspace isolation")
    class CrossWorkspaceIsolation {

        @Test
        @DisplayName("should return 404 for cross-workspace GET list")
        void shouldReturn404ForCrossWorkspaceList() {
            String fakeWs = UUID.randomUUID().toString();
            String path = "/workspaces/" + fakeWs + "/projects/" + projectId
                    + "/milestones/" + milestoneId + "/tasks";

            ResponseEntity<Map> response = restTemplate.exchange(
                    path, HttpMethod.GET,
                    new HttpEntity<>(ownerReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 for cross-workspace GET detail")
        void shouldReturn404ForCrossWorkspaceDetail() {
            String fakeWs = UUID.randomUUID().toString();
            String path = "/workspaces/" + fakeWs + "/projects/" + projectId
                    + "/milestones/" + milestoneId + "/tasks/" + taskId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    path, HttpMethod.GET,
                    new HttpEntity<>(ownerReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Cross-project isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Cross-project isolation")
    class CrossProjectIsolation {

        @Test
        @DisplayName("should return 404 for cross-project GET list")
        void shouldReturn404ForCrossProjectList() {
            String fakeProj = UUID.randomUUID().toString();
            String path = "/workspaces/" + workspaceId + "/projects/" + fakeProj
                    + "/milestones/" + milestoneId + "/tasks";

            ResponseEntity<Map> response = restTemplate.exchange(
                    path, HttpMethod.GET,
                    new HttpEntity<>(ownerReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Cross-milestone isolation ─────────────────────────────────────────

    @Nested
    @DisplayName("Cross-milestone isolation")
    class CrossMilestoneIsolation {

        @Test
        @DisplayName("should return 404 for cross-milestone GET detail")
        void shouldReturn404ForCrossMilestoneDetail() {
            String fakeMs = UUID.randomUUID().toString();
            String path = "/workspaces/" + workspaceId + "/projects/" + projectId
                    + "/milestones/" + fakeMs + "/tasks/" + taskId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    path, HttpMethod.GET,
                    new HttpEntity<>(ownerReadHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
