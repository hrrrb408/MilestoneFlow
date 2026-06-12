package com.milestoneflow.project.integration;

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
 * Security integration tests for project archive/restore APIs.
 */
@DisplayName("Project Archive Security IT")
class ProjectArchiveSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String OWNER_EMAIL = "archive-sec-owner@example.com";
    private static final String MEMBER_EMAIL = "archive-sec-member@example.com";
    private static final String OUTSIDER_EMAIL = "archive-sec-outsider@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "archive-sec-ws";

    private String workspaceId;
    private HttpHeaders ownerAuthHeaders;

    @BeforeEach
    void setUp() {
        cleanAll();
        createActiveUser(OWNER_EMAIL);
        createActiveUser(MEMBER_EMAIL);
        createActiveUser(OUTSIDER_EMAIL);

        String ownerCookie = loginAndGetCookie(OWNER_EMAIL);
        ownerAuthHeaders = authHeadersWithCsrf(ownerCookie);

        // Owner creates workspace
        var wsBody = Map.of("name", "Archive Sec WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, ownerAuthHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        ownerAuthHeaders = authHeadersWithCsrf(ownerCookie);
    }

    private void cleanAll() {
        jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
        for (String email : new String[]{OWNER_EMAIL, MEMBER_EMAIL, OUTSIDER_EMAIL}) {
            String norm = email.toLowerCase();
            jdbc.update("DELETE FROM workspace_membership WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
            jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
            jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", norm);
            jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", norm);
        }
        jdbc.update("DELETE FROM workspace WHERE slug = ?", WS_SLUG);
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

    private String projectBasePath() {
        return "/workspaces/" + workspaceId + "/projects";
    }

    private String createProject() {
        var body = Map.of("name", "Security Test Project");
        ResponseEntity<Map> response = restTemplate.exchange(
                projectBasePath(), HttpMethod.POST,
                new HttpEntity<>(body, ownerAuthHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("id");
    }

    // ── Anonymous access ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Anonymous archive/restore")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous archive with 401")
        void shouldRejectAnonymousArchive() {
            String projectId = createProject();
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous restore with 401")
        void shouldRejectAnonymousRestore() {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + UUID.randomUUID() + "/restore", HttpMethod.POST,
                    new HttpEntity<>(headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Cross-user access ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-owner access")
    class NonOwnerAccess {

        @Test
        @DisplayName("should block non-member from archiving")
        void shouldBlockOutsiderArchive() {
            String projectId = createProject();
            HttpHeaders outsiderHeaders = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(outsiderHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block non-member from restoring")
        void shouldBlockOutsiderRestore() {
            String projectId = createProject();
            // Archive first
            restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(ownerAuthHeaders), Map.class);

            HttpHeaders outsiderHeaders = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/restore", HttpMethod.POST,
                    new HttpEntity<>(outsiderHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── Cross-workspace isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Cross-workspace archive/restore")
    class CrossWorkspaceIsolation {

        @Test
        @DisplayName("should return 404 when archiving with wrong workspace")
        void shouldReturn404WrongWorkspaceArchive() {
            String projectId = createProject();
            String fakeWorkspaceId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + fakeWorkspaceId + "/projects/" + projectId + "/archive",
                    HttpMethod.POST, new HttpEntity<>(ownerAuthHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when restoring with wrong workspace")
        void shouldReturn404WrongWorkspaceRestore() {
            String fakeWorkspaceId = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/workspaces/" + fakeWorkspaceId + "/projects/" + UUID.randomUUID() + "/restore",
                    HttpMethod.POST, new HttpEntity<>(ownerAuthHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── CSRF requirement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CSRF protection")
    class CsrfProtection {

        @Test
        @DisplayName("should reject archive without CSRF token")
        void shouldRejectArchiveWithoutCsrf() {
            String projectId = createProject();

            // Get all cookies from a full auth flow (includes session cookie)
            HttpHeaders noCsrfHeaders = authHeadersWithSessionButNoCsrf(OWNER_EMAIL);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + projectId + "/archive", HttpMethod.POST,
                    new HttpEntity<>(noCsrfHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("should reject restore without CSRF token")
        void shouldRejectRestoreWithoutCsrf() {
            HttpHeaders noCsrfHeaders = authHeadersWithSessionButNoCsrf(OWNER_EMAIL);

            ResponseEntity<Map> response = restTemplate.exchange(
                    projectBasePath() + "/" + UUID.randomUUID() + "/restore", HttpMethod.POST,
                    new HttpEntity<>(noCsrfHeaders), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Builds auth headers with session cookie but without CSRF token header.
     * This simulates a valid session that is missing the CSRF token.
     */
    private HttpHeaders authHeadersWithSessionButNoCsrf(String email) {
        // Login to get MF_ACCESS cookie
        var loginBody = Map.of("email", email, "password", PASSWORD);
        var loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders), Map.class);
        String accessCookie = extractCookie(loginResponse, "MF_ACCESS=");

        // Call /auth/me to get session cookie (but intentionally skip X-XSRF-TOKEN header)
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", accessCookie);
        ResponseEntity<Void> csrfResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        var setCookies = csrfResponse.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            for (String cookie : setCookies) {
                // Include all cookies EXCEPT skip adding the X-XSRF-TOKEN header
                headers.add("Cookie", cookie.split(";")[0]);
            }
        }
        // Intentionally do NOT add X-XSRF-TOKEN header
        return headers;
    }
}
