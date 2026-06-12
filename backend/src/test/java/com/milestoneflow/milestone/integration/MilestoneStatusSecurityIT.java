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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security integration tests for milestone complete/reopen APIs.
 *
 * <p>Verifies that only OWNER can complete/reopen, that anonymous
 * users are rejected, and that non-members receive 404.
 */
@DisplayName("Milestone Status Security IT")
class MilestoneStatusSecurityIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String OWNER_EMAIL = "ms-status-sec-owner@example.com";
    private static final String OUTSIDER_EMAIL = "ms-status-sec-outside@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String WS_SLUG = "ms-status-sec-ws";

    private String workspaceId;
    private String projectId;
    private String milestoneId;
    private HttpHeaders ownerHeaders;
    private HttpHeaders outsiderHeadersOnly;

    @BeforeEach
    void setUp() {
        cleanAll();
        createActiveUser(OWNER_EMAIL);
        createActiveUser(OUTSIDER_EMAIL);

        String ownerCookie = loginAndGetCookie(OWNER_EMAIL);
        String outsiderCookie = loginAndGetCookie(OUTSIDER_EMAIL);

        ownerHeaders = authHeadersWithCsrf(ownerCookie);
        outsiderHeadersOnly = authHeadersOnly(outsiderCookie);

        // Owner creates workspace
        var wsBody = Map.of("name", "Sec WS", "slug", WS_SLUG);
        ResponseEntity<Map> wsResponse = restTemplate.exchange(
                "/workspaces", HttpMethod.POST,
                new HttpEntity<>(wsBody, ownerHeaders), Map.class);
        assertThat(wsResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var wsData = (Map<String, Object>) wsResponse.getBody().get("data");
        workspaceId = (String) wsData.get("id");

        // Owner creates project
        ownerHeaders = authHeadersWithCsrf(ownerCookie);
        var projBody = Map.of("name", "Sec Project");
        ResponseEntity<Map> projResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects", HttpMethod.POST,
                new HttpEntity<>(projBody, ownerHeaders), Map.class);
        assertThat(projResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var projData = (Map<String, Object>) projResponse.getBody().get("data");
        projectId = (String) projData.get("id");

        // Owner creates a milestone
        ownerHeaders = authHeadersWithCsrf(ownerCookie);
        var msBody = Map.of("title", "Security Test Milestone");
        ResponseEntity<Map> msResponse = restTemplate.exchange(
                "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones",
                HttpMethod.POST, new HttpEntity<>(msBody, ownerHeaders), Map.class);
        assertThat(msResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        var msData = (Map<String, Object>) msResponse.getBody().get("data");
        milestoneId = (String) msData.get("id");

        ownerHeaders = authHeadersWithCsrf(ownerCookie);
        outsiderHeadersOnly = authHeadersOnly(outsiderCookie);
    }

    private void cleanAll() {
        for (String email : new String[]{OWNER_EMAIL, OUTSIDER_EMAIL}) {
            String norm = email.toLowerCase();
            jdbc.update("DELETE FROM milestone WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
            jdbc.update("DELETE FROM project WHERE workspace_id IN (SELECT id FROM workspace WHERE slug = ?)", WS_SLUG);
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

    private HttpHeaders authHeadersOnly(String accessCookie) {
        var headers = new HttpHeaders();
        headers.add("Cookie", accessCookie);
        return headers;
    }

    private String basePath() {
        return "/workspaces/" + workspaceId + "/projects/" + projectId + "/milestones";
    }

    @Nested
    @DisplayName("Non-member (different user) complete/reopen")
    class NonOwnerAccess {

        @Test
        @DisplayName("should block non-member from completing milestone with 404")
        void shouldBlockNonMemberComplete() {
            HttpHeaders outsiderHeadersCsrf = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(outsiderHeadersCsrf), Map.class);

            // Non-member receives 404 to prevent resource existence leakage
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block non-member from reopening milestone with 404")
        void shouldBlockNonMemberReopen() {
            // Owner completes first
            restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(ownerHeaders), Map.class);

            HttpHeaders outsiderHeadersCsrf = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(outsiderHeadersCsrf), Map.class);

            // Non-member receives 404 to prevent resource existence leakage
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Non-member access")
    class NonMemberAccess {

        @Test
        @DisplayName("should block outsider from completing milestone with 404")
        void shouldBlockOutsiderComplete() {
            HttpHeaders outsiderHeadersCsrf = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(outsiderHeadersCsrf), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should block outsider from reopening milestone with 404")
        void shouldBlockOutsiderReopen() {
            // Owner completes first
            restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(ownerHeaders), Map.class);

            HttpHeaders outsiderHeadersCsrf = authHeadersWithCsrf(loginAndGetCookie(OUTSIDER_EMAIL));

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(outsiderHeadersCsrf), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Anonymous access")
    class AnonymousAccess {

        @Test
        @DisplayName("should reject anonymous complete with 401")
        void shouldRejectAnonymousComplete() {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject anonymous reopen with 401")
        void shouldRejectAnonymousReopen() {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    basePath() + "/" + milestoneId + "/reopen",
                    HttpMethod.POST, new HttpEntity<>(headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
