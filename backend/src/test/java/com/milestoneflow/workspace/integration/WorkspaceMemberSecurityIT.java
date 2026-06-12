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
 * Security integration tests for workspace member query APIs.
 *
 * <p>Verifies authentication, cross-workspace isolation, membership-state
 * isolation (PENDING/REMOVED denied), and that responses never leak the
 * existence of a workspace or sensitive fields.
 */
@DisplayName("Workspace Member Security IT")
class WorkspaceMemberSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String OWNER_EMAIL = "mem-sec-owner@example.com";
    private static final String OTHER_EMAIL = "mem-sec-other@example.com";
    private static final String PENDING_EMAIL = "mem-sec-pending@example.com";
    private static final String REMOVED_EMAIL = "mem-sec-removed@example.com";
    private static final String PASSWORD = "test-password-123";

    private String workspaceId;

    @BeforeEach
    void setUp() {
        cleanAll();
        createActiveUser(OWNER_EMAIL);
        createActiveUser(OTHER_EMAIL);
        createActiveUser(PENDING_EMAIL);
        createActiveUser(REMOVED_EMAIL);

        // Owner creates the workspace under test
        String ownerCookie = loginAndGetCookie(OWNER_EMAIL);
        var createBody = Map.of("name", "Sec Member WS", "slug", "sec-member-ws");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(createBody, authHeadersWithCsrf(ownerCookie)), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) createResponse.getBody().get("data");
        workspaceId = (String) data.get("id");

        // Grant PENDING and REMOVED memberships in the owner's workspace
        grantMembership(PENDING_EMAIL, "PENDING", null);
        grantMembership(REMOVED_EMAIL, "REMOVED", "now()");
    }

    private void cleanAll() {
        for (String email : new String[]{OWNER_EMAIL, OTHER_EMAIL, PENDING_EMAIL, REMOVED_EMAIL}) {
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

    private void grantMembership(String email, String status, String endedAtExpr) {
        String endedAtClause = endedAtExpr != null ? endedAtExpr : "NULL";
        jdbc.update("""
            INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at, version, created_at, updated_at)
            SELECT gen_random_uuid(), w.id, u.id, 'OWNER', ?::varchar, now(), %s, 0, now(), now()
              FROM workspace w, app_user u
             WHERE w.slug = 'sec-member-ws' AND u.email_normalized = ?
            """.formatted(endedAtClause), status, email.toLowerCase());
    }

    private String loginAndGetCookie(String email) {
        var body = Map.of("email", email, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        return response.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith("MF_ACCESS="))
                .findFirst()
                .orElse(null);
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

    private HttpHeaders authHeadersOnly(String accessCookie) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessCookie);
        return headers;
    }

    // ── Anonymous access ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Anonymous access")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous member list with 401")
        void shouldRejectAnonymousList() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous members/me with 401")
        void shouldRejectAnonymousMe() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Cross-workspace / non-member isolation ───────────────────────────

    @Nested
    @DisplayName("Non-member access")
    class NonMemberAccess {

        @Test
        @DisplayName("should block non-member from listing members with 404")
        void shouldBlockNonMemberList() {
            String cookie = loginAndGetCookie(OTHER_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block non-member from members/me with 404")
        void shouldBlockNonMemberMe() {
            String cookie = loginAndGetCookie(OTHER_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Membership-state isolation ───────────────────────────────────────

    @Nested
    @DisplayName("Membership-state isolation")
    class MembershipStateIsolation {

        @Test
        @DisplayName("should block PENDING membership from listing members with 404")
        void shouldBlockPendingList() {
            String cookie = loginAndGetCookie(PENDING_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block PENDING membership from members/me with 404")
        void shouldBlockPendingMe() {
            String cookie = loginAndGetCookie(PENDING_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block REMOVED membership from listing members with 404")
        void shouldBlockRemovedList() {
            String cookie = loginAndGetCookie(REMOVED_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block REMOVED membership from members/me with 404")
        void shouldBlockRemovedMe() {
            String cookie = loginAndGetCookie(REMOVED_EMAIL);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members/me", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Existence leakage ────────────────────────────────────────────────

    @Nested
    @DisplayName("Existence leakage")
    class ExistenceLeakage {

        @Test
        @DisplayName("non-existent workspace and inaccessible workspace return identical 404")
        void shouldNotLeakWorkspaceExistence() {
            String cookie = loginAndGetCookie(OTHER_EMAIL);

            ResponseEntity<Map> inaccessible = restTemplate.exchange(
                    "/workspaces/" + workspaceId + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);
            ResponseEntity<Map> nonexistent = restTemplate.exchange(
                    "/workspaces/" + UUID.randomUUID() + "/members", HttpMethod.GET,
                    new HttpEntity<>(authHeadersOnly(cookie)), Map.class);

            assertThat(inaccessible.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(nonexistent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(inaccessible.getBody().get("code"))
                    .isEqualTo(nonexistent.getBody().get("code"));
        }
    }
}
