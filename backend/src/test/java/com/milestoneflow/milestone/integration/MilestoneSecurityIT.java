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
 * Security integration tests for milestone APIs.
 */
@DisplayName("Milestone Security IT")
class MilestoneSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String USER_A_EMAIL = "ms-sec-a@example.com";
    private static final String USER_B_EMAIL = "ms-sec-b@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG_A = "ms-sec-ws-a";

    private String workspaceIdA;
    private String projectIdA;
    private HttpHeaders authHeadersA;
    private HttpHeaders authHeadersOnlyA;
    private HttpHeaders authHeadersOnlyB;

    @BeforeEach
    void setUp() {
        cleanAll();
        createActiveUser(USER_A_EMAIL);
        createActiveUser(USER_B_EMAIL);

        String cookieA = loginAndGetCookie(USER_A_EMAIL);
        String cookieB = loginAndGetCookie(USER_B_EMAIL);
        authHeadersA = authHeadersWithCsrf(cookieA);
        authHeadersOnlyA = authHeadersOnly(cookieA);
        authHeadersOnlyB = authHeadersOnly(cookieB);

        // User A creates workspace
        var wsBody = Map.of("name", "A's Workspace", "slug", WS_SLUG_A);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, authHeadersA), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceIdA = (String) wsData.get("id");

        // User A creates project
        authHeadersA = authHeadersWithCsrf(cookieA);
        var projBody = Map.of("name", "A's Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceIdA + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, authHeadersA), Map.class);
        assertThat(projResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectIdA = (String) projData.get("id");

        authHeadersA = authHeadersWithCsrf(cookieA);
        authHeadersOnlyA = authHeadersOnly(cookieA);
        authHeadersOnlyB = authHeadersOnly(cookieB);
    }

    private void cleanAll() {
        for (String email : new String[]{USER_A_EMAIL, USER_B_EMAIL}) {
            String norm = email.toLowerCase();
            jdbc.update("DELETE FROM milestone WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG_A);
            jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG_A);
            jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
            jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
            jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
        }
        jdbc.update("DELETE FROM workspace WHERE slug = ?", WS_SLUG_A);
    }

    private void createActiveUser(String email) {
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, email, email.toLowerCase(), "User " + email, encodedPassword);
    }

    private String loginAndGetCookie(String email) {
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

    private HttpHeaders authHeadersWithCsrf(String accessCookie) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessCookie);

        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Void.class);
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

    private HttpHeaders authHeadersOnly(String accessCookie) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessCookie);
        return headers;
    }

    private String milestoneBasePath() {
        return "/workspaces/" + workspaceIdA + "/projects/" + projectIdA + "/milestones";
    }

    @Nested
    @DisplayName("Cross-user access")
    class CrossUserAccess {

        @Test
        @DisplayName("should block user B from listing user A's milestones")
        void shouldBlockCrossUserList() {
            var body = Map.of("title", "A's Milestone");
            restTemplate.exchange(milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeadersA), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnlyB), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block user B from creating milestone in user A's project")
        void shouldBlockCrossUserCreate() {
            var body = Map.of("title", "Infiltrator");
            HttpHeaders headersB = authHeadersWithCsrf(loginAndGetCookie(USER_B_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headersB), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block user B from viewing user A's milestone")
        void shouldBlockCrossUserGet() {
            var body = Map.of("title", "Secret Milestone");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeadersA), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) data.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnlyB), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block user B from updating user A's milestone")
        void shouldBlockCrossUserUpdate() {
            var body = Map.of("title", "Protected");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeadersA), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) data.get("id");

            var updateBody = Map.of("title", "Hacked!");
            HttpHeaders headersB = authHeadersWithCsrf(loginAndGetCookie(USER_B_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + milestoneId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, headersB), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Cross-project isolation")
    class CrossProjectIsolation {

        @Test
        @DisplayName("should return 404 when accessing milestone with wrong project")
        void shouldReturn404WrongProject() {
            var body = Map.of("title", "Isolated Milestone");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeadersA), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) data.get("id");

            // Try accessing from a different (non-existent) project
            String fakeProjectId = UUID.randomUUID().toString();
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceIdA + "/projects/" + fakeProjectId + "/milestones/" + milestoneId,
                    HttpMethod.GET, new HttpEntity<>(authHeadersOnlyA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when accessing milestone with wrong workspace")
        void shouldReturn404WrongWorkspace() {
            var body = Map.of("title", "Workspace Isolated");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, authHeadersA), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String milestoneId = (String) data.get("id");

            String fakeWorkspaceId = UUID.randomUUID().toString();
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + fakeWorkspaceId + "/projects/" + projectIdA + "/milestones/" + milestoneId,
                    HttpMethod.GET, new HttpEntity<>(authHeadersOnlyA), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Anonymous access")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous milestone creation")
        void shouldRejectAnonymousCreate() {
            var body = Map.of("title", "Anonymous Milestone");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous milestone list")
        void shouldRejectAnonymousList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous milestone detail")
        void shouldRejectAnonymousDetail() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + UUID.randomUUID(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous milestone update")
        void shouldRejectAnonymousUpdate() {
            var body = Map.of("title", "Hacked");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    milestoneBasePath() + "/" + UUID.randomUUID(), HttpMethod.PATCH,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
