package com.milestoneflow.identity.integration;

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
 * Integration tests for logout and logout-all flows against PostgreSQL 17.
 */
@DisplayName("Logout Flow IT")
class LogoutFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "logout-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", EMAIL.toLowerCase());
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Logout IT User", encodedPassword);

        userId = jdbc.queryForObject("SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    private ResponseEntity<Map> doLogin() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    private HttpHeaders cookiesFrom(ResponseEntity<?> response) {
        var headers = new HttpHeaders();
        response.getHeaders().get("Set-Cookie").forEach(c -> headers.add("Cookie", c.split(";")[0]));
        return headers;
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        @DisplayName("logout revokes current session")
        void revokesSession() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                    "/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Session should be REVOKED with LOGOUT reason
            Map<String, Object> session = jdbc.queryForMap(
                    "SELECT status, revoke_reason FROM auth_session WHERE user_id = ?::uuid",
                    userId);
            assertThat(session.get("status")).isEqualTo("REVOKED");
            assertThat(session.get("revoke_reason")).isEqualTo("LOGOUT");
        }

        @Test
        @DisplayName("logout clears cookies")
        void clearsCookies() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                    "/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            var setCookies = logoutResponse.getHeaders().get("Set-Cookie");
            assertThat(setCookies).isNotNull();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_ACCESS="))).isTrue();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_REFRESH="))).isTrue();
        }

        @Test
        @DisplayName("after logout, /me returns 401")
        void meFailsAfterLogout() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            // Logout
            restTemplate.exchange("/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            // /me with old access cookie should fail
            ResponseEntity<Map> meResponse = restTemplate.exchange(
                    "/auth/me", HttpMethod.GET,
                    new HttpEntity<>(cookies), Map.class);

            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("after logout, refresh returns 401")
        void refreshFailsAfterLogout() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            // Logout
            restTemplate.exchange("/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            // Refresh with old refresh cookie should fail
            ResponseEntity<Map> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("logout does not delete session from database")
        void doesNotDeleteSession() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            Integer countBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid",
                    Integer.class, userId);
            assertThat(countBefore).isEqualTo(1);

            restTemplate.exchange("/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            Integer countAfter = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid",
                    Integer.class, userId);
            assertThat(countAfter).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("POST /auth/logout-all")
    class LogoutAll {

        @Test
        @DisplayName("logout-all revokes all sessions for user")
        void revokesAllSessions() {
            // Login twice to create two sessions
            doLogin();
            doLogin();

            Integer activeBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(activeBefore).isEqualTo(2);

            // Use the second login's cookies
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                    "/auth/logout-all", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // All sessions should be REVOKED with LOGOUT_ALL reason
            Integer activeAfter = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(activeAfter).isEqualTo(0);

            Integer revokedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'REVOKED' AND revoke_reason = 'LOGOUT_ALL'",
                    Integer.class, userId);
            assertThat(revokedCount).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("logout-all does not affect other users")
        void doesNotAffectOtherUsers() {
            // Create a second user
            String otherEmail = "other-logout-it@example.com";
            String otherPassword = passwordEncoder.encode(PASSWORD);
            jdbc.update("""
                INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
                """, otherEmail, otherEmail.toLowerCase(), "Other User", otherPassword);

            // Login as both users
            doLogin();
            var otherBody = Map.of("email", otherEmail, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(otherBody, headers), Map.class);

            // Logout-all as first user
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);
            restTemplate.exchange("/auth/logout-all", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            // Other user's sessions should still be ACTIVE
            String otherUserId = jdbc.queryForObject(
                    "SELECT id::text FROM app_user WHERE email_normalized = ?",
                    String.class, otherEmail.toLowerCase());
            Integer otherActive = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, otherUserId);
            assertThat(otherActive).isGreaterThan(0);
        }

        @Test
        @DisplayName("logout-all is idempotent when no active sessions remain")
        void idempotentWhenNoSessions() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            // First logout-all
            restTemplate.exchange("/auth/logout-all", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            // Second logout-all should still succeed
            ResponseEntity<Void> secondLogout = restTemplate.exchange(
                    "/auth/logout-all", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            // The second call gets 401 because the access cookie is now invalid
            // (session was revoked by first logout-all)
            assertThat(secondLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
