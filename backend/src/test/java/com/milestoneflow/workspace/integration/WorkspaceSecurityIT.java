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
 * Security integration tests for workspace APIs.
 *
 * <p>Verifies cross-user access is blocked and
 * unauthorized operations are rejected.
 */
@DisplayName("Workspace Security IT")
class WorkspaceSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String USER_A_EMAIL = "ws-sec-a@example.com";
    private static final String USER_B_EMAIL = "ws-sec-b@example.com";
    private static final String PASSWORD = "test-password-123";

    @BeforeEach
    void setUp() {
        cleanAll();

        // Create user A (with workspace)
        createActiveUser(USER_A_EMAIL);
        // Create user B (no workspace)
        createActiveUser(USER_B_EMAIL);
    }

    private void cleanAll() {
        for (String email : new String[]{USER_A_EMAIL, USER_B_EMAIL}) {
            String norm = email.toLowerCase();
            jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
            jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
            jdbc.update("DELETE FROM workspace WHERE created_by IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
        }
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
        var setCookies = response.getHeaders().get("Set-Cookie");
        return setCookies.stream()
                .filter(c -> c.startsWith("MF_ACCESS="))
                .findFirst()
                .orElse(null);
    }

    private HttpHeaders authHeadersWithCsrf(String accessCookie) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessCookie);

        // Get CSRF token
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        String csrfCookie = csrfResponse.getHeaders().getFirst("Set-Cookie");
        if (csrfCookie != null) {
            String csrfToken = csrfCookie.split("XSRF-TOKEN=")[1].split(";")[0];
            headers.add("X-XSRF-TOKEN", csrfToken);
            headers.add("Cookie", csrfCookie);
        }
        return headers;
    }

    private HttpHeaders authHeadersOnly(String accessCookie) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessCookie);
        return headers;
    }

    // ── Cross-user access ────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-user access")
    class CrossUserAccess {

        @Test
        @DisplayName("should block user B from reading user A's workspace")
        void shouldBlockCrossUserRead() {
            // User A creates workspace
            String cookieA = loginAndGetCookie(USER_A_EMAIL);
            var createBody = Map.of("name", "A's Workspace", "slug", "a-workspace-sec");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(createBody, authHeadersWithCsrf(cookieA)), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String workspaceId = (String) data.get("id");

            // User B tries to read it
            String cookieB = loginAndGetCookie(USER_B_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId, HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookieB)), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block user B from updating user A's workspace")
        void shouldBlockCrossUserUpdate() {
            // User A creates workspace
            String cookieA = loginAndGetCookie(USER_A_EMAIL);
            var createBody = Map.of("name", "A's Workspace", "slug", "a-workspace-sec2");
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(createBody, authHeadersWithCsrf(cookieA)), Map.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) createResponse.getBody().get("data");
            String workspaceId = (String) data.get("id");

            // User B tries to update it
            String cookieB = loginAndGetCookie(USER_B_EMAIL);
            var updateBody = Map.of("name", "Hacked!");
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId, HttpMethod.PATCH,
                    new HttpEntity<>(updateBody, authHeadersWithCsrf(cookieB)), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Anonymous access ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Anonymous access")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous workspace creation")
        void shouldRejectAnonymousCreate() {
            var body = Map.of("name", "Anonymous WS", "slug", "anon-ws");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous workspace query")
        void shouldRejectAnonymousQuery() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/current", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous workspace detail")
        void shouldRejectAnonymousDetail() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + UUID.randomUUID(), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous workspace update")
        void shouldRejectAnonymousUpdate() {
            var body = Map.of("name", "Hacked");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + UUID.randomUUID(), HttpMethod.PATCH,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
