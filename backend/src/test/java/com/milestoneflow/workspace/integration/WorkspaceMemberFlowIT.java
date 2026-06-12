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
 * Integration tests for workspace member query flows.
 *
 * <p>Uses Testcontainers PostgreSQL 17. Tests the full stack from
 * HTTP request through to database state verification for the member
 * roster and current-membership endpoints.
 */
@DisplayName("Workspace Member Flow IT")
class WorkspaceMemberFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "member-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String accessToken;
    private String workspaceId;
    private String userId;

    @BeforeEach
    void setUp() {
        cleanData();

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Member IT Owner", encodedPassword);

        userId = jdbc.queryForObject(
                "SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());

        // Login
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        accessToken = loginResponse.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith("MF_ACCESS="))
                .findFirst()
                .orElse(null);

        // Create workspace
        var createBody = Map.of("name", "Member Flow WS", "slug", "member-flow-ws");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(createBody, authHeadersWithCsrf()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) createResponse.getBody().get("data");
        workspaceId = (String) data.get("id");
    }

    private void cleanData() {
        String norm = EMAIL.toLowerCase();
        jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        // created_by is NULL in tests (AuditorAware returns empty), so remove the
        // workspace by its fixed slug — otherwise it persists and the next setUp's
        // same-slug creation conflicts with 409 SLUG_ALREADY_EXISTS.
        jdbc.update("DELETE FROM workspace WHERE slug = 'member-flow-ws'");
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
    }

    private HttpHeaders authHeadersWithCsrf() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessToken);

        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(authHeadersOnly()), Void.class);
        var setCookies = csrfResponse.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            String xsrfCookie = setCookies.stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN="))
                    .findFirst()
                    .orElse(null);
            if (xsrfCookie != null) {
                headers.add("X-XSRF-TOKEN", xsrfCookie.split("XSRF-TOKEN=")[1].split(";")[0]);
                headers.add("Cookie", xsrfCookie.split(";")[0]);
            }
        }
        return headers;
    }

    private HttpHeaders authHeadersOnly() {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessToken);
        return headers;
    }

    // ── GET /workspaces/{id}/members ─────────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/members")
    class ListMembers {

        @Test
        @DisplayName("should return the OWNER in the member roster")
        void shouldReturnOwnerMember() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("meta");
            assertThat(((Map<?, ?>) response.getBody().get("meta")).get("requestId")).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("workspaceId")).isEqualTo(workspaceId);

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> members = (java.util.List<Map<String, Object>>) data.get("members");
            assertThat(members).hasSize(1);

            Map<String, Object> owner = members.get(0);
            assertThat(owner.get("userId")).isEqualTo(userId);
            assertThat(owner.get("email")).isEqualTo(EMAIL);
            assertThat(owner.get("displayName")).isEqualTo("Member IT Owner");
            assertThat(owner.get("role")).isEqualTo("OWNER");
            assertThat(owner.get("status")).isEqualTo("ACTIVE");
            assertThat(owner.get("joinedAt")).isNotNull();
        }

        @Test
        @DisplayName("should not expose sensitive fields in the member response")
        void shouldNotExposeSensitiveFields() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String body = response.getBody().toString();

            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("password_hash");
            assertThat(body).doesNotContain("emailNormalized");
            assertThat(body).doesNotContain("email_normalized");
            assertThat(body).doesNotContain("lastLoginAt");
            assertThat(body).doesNotContain("tokenHash");
            assertThat(body).doesNotContain("verificationToken");
        }
    }

    // ── GET /workspaces/{id}/members/me ──────────────────────────────────

    @Nested
    @DisplayName("GET /workspaces/{id}/members/me")
    class CurrentMembership {

        @Test
        @DisplayName("should return the OWNER membership for the current user")
        void shouldReturnOwnerMembership() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("meta");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("workspaceId")).isEqualTo(workspaceId);
            assertThat(data.get("userId")).isEqualTo(userId);
            assertThat(data.get("role")).isEqualTo("OWNER");
            assertThat(data.get("status")).isEqualTo("ACTIVE");
            assertThat(data.get("joinedAt")).isNotNull();
        }

        @Test
        @DisplayName("should return 404 for a non-existent workspace")
        void shouldReturn404ForUnknownWorkspace() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + UUID.randomUUID() + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("members/me response should not expose sensitive fields")
        void shouldNotExposeSensitiveFields() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String body = response.getBody().toString();

            assertThat(body).doesNotContain("email");
            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("emailNormalized");
        }
    }
}
