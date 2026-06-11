package com.milestoneflow.workspace.integration;

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
 * Integration tests for workspace creation and query flows.
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification.
 */
@DisplayName("Workspace Flow IT")
class WorkspaceFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "workspace-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        // Clean up all test data
        cleanWorkspaceData();

        // Create a verified ACTIVE user
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Workspace IT User", encodedPassword);

        userId = jdbc.queryForObject(
                "SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());

        // Login to get access token cookie
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        var setCookies = loginResponse.getHeaders().get("Set-Cookie");
        accessToken = setCookies.stream()
                .filter(c -> c.startsWith("MF_ACCESS="))
                .findFirst()
                .orElse(null);
    }

    private void cleanWorkspaceData() {
        String testEmail = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", testEmail);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", testEmail);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace WHERE created_by IN (SELECT id FROM app_user WHERE email_normalized = ?)", testEmail);
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", testEmail);
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", testEmail);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", testEmail);
    }

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.add("Cookie", accessToken);
        }

        // Get CSRF token — trigger CSRF token generation via a GET request
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(authHeadersOnly()), Void.class);

        // Search for XSRF-TOKEN cookie among all Set-Cookie headers
        var setCookies = csrfResponse.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            String xsrfCookie = setCookies.stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN="))
                    .findFirst()
                    .orElse(null);
            if (xsrfCookie != null) {
                String csrfToken = xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
                headers.add("X-XSRF-TOKEN", csrfToken);
                headers.add("Cookie", xsrfCookie.split(";")[0]);
            }
        }
        return headers;
    }

    private HttpHeaders authHeadersOnly() {
        var headers = new HttpHeaders();
        if (accessToken != null) {
            headers.add("Cookie", accessToken);
        }
        return headers;
    }

    // ── POST /workspaces ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /workspaces")
    class CreateWorkspace {

        @Test
        @DisplayName("should create workspace with 201")
        void shouldCreateWorkspace() {
            var body = Map.of(
                    "name", "My Workspace",
                    "slug", "my-workspace",
                    "timezone", "Asia/Taipei",
                    "defaultCurrency", "TWD"
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("My Workspace");
            assertThat(data.get("slug")).isEqualTo("my-workspace");
            assertThat(data.get("status")).isEqualTo("ACTIVE");
            assertThat(data.get("role")).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("should persist workspace in database")
        void shouldPersistWorkspace() {
            var body = Map.of("name", "DB Test", "slug", "db-test");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workspace WHERE slug = 'db-test'",
                    Integer.class);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should create OWNER membership in database")
        void shouldCreateOwnerMembership() {
            var body = Map.of("name", "Membership Test", "slug", "membership-test");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            Map<String, Object> membership = jdbc.queryForMap(
                    "SELECT * FROM workspace_membership WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    userId);

            assertThat(membership.get("role")).isEqualTo("OWNER");
            assertThat(membership.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should reject duplicate slug with 409")
        void shouldRejectDuplicateSlug() {
            var body = Map.of("name", "First", "slug", "duplicate-slug");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            var body2 = Map.of("name", "Second", "slug", "duplicate-slug");
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).isEqualTo("WORKSPACE_SLUG_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("should reject second workspace for same user with 409")
        void shouldRejectSecondWorkspace() {
            var body = Map.of("name", "First", "slug", "first-ws");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            var body2 = Map.of("name", "Second", "slug", "second-ws");
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body2, authHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject validation failure with 422")
        void shouldRejectValidation() {
            var body = Map.of("name", "", "slug", "ab"); // too short slug

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ── GET /workspaces/current ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/current")
    class GetCurrentWorkspace {

        @Test
        @DisplayName("should return workspace when user has one")
        void shouldReturnWorkspace() {
            // First create a workspace
            var body = Map.of("name", "Current Test", "slug", "current-test");
            restTemplate.exchange("/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/current", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("Current Test");
            assertThat(data.get("role")).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("should return 404 when user has no workspace")
        void shouldReturn404WhenNone() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/current", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── GET /workspaces/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}")
    class GetWorkspace {

        @Test
        @DisplayName("should return workspace detail")
        void shouldReturnDetail() {
            // Create workspace first
            var body = Map.of("name", "Detail Test", "slug", "detail-test");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String workspaceId = (String) data.get("id");

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var detail = (Map<String, Object>) response.getBody().get("data");
            assertThat(detail.get("name")).isEqualTo("Detail Test");
        }

        @Test
        @DisplayName("should return 404 for non-existent workspace")
        void shouldReturn404ForNonExistent() {
            String randomId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + randomId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── PATCH /workspaces/{id} ───────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /workspaces/{id}")
    class UpdateWorkspace {

        @Test
        @DisplayName("should update workspace name")
        void shouldUpdateName() {
            // Create workspace
            var body = Map.of("name", "Original", "slug", "update-test");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String workspaceId = (String) data.get("id");

            // Update
            var updateBody = Map.of("name", "Updated Name");
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var updated = (Map<String, Object>) response.getBody().get("data");
            assertThat(updated.get("name")).isEqualTo("Updated Name");
        }
    }

    // ── Unauthenticated access ───────────────────────────────────────────

    @Nested
    @DisplayName("Unauthenticated access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("should reject unauthenticated POST with 401")
        void shouldRejectUnauthenticatedPost() {
            var body = Map.of("name", "Test", "slug", "test");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject unauthenticated GET with 401")
        void shouldRejectUnauthenticatedGet() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/current", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
