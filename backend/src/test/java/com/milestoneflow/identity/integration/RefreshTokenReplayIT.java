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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for refresh token replay detection against PostgreSQL 17.
 *
 * <p>Verifies that reusing an already-rotated refresh token triggers
 * family-wide revocation.
 */
@DisplayName("Refresh Token Replay IT")
class RefreshTokenReplayIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "replay-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", EMAIL.toLowerCase());

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Replay IT User", encodedPassword);

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

    private String extractRefreshCookie(ResponseEntity<?> response) {
        return response.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith("MF_REFRESH="))
                .map(c -> c.split(";")[0])
                .findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Replay detection")
    class ReplayDetection {

        @Test
        @DisplayName("reusing old refresh token returns 401 AUTH_REFRESH_TOKEN_REUSED")
        void replayReturns401() {
            var loginResponse = doLogin();
            HttpHeaders oldCookies = cookiesFrom(loginResponse);

            // First refresh succeeds
            var refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Reuse old refresh cookie — should trigger replay
            ResponseEntity<Map> replayResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(replayResponse.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_REUSED");
        }

        @Test
        @DisplayName("replay revokes entire session family")
        void replayRevokesFamily() {
            var loginResponse = doLogin();
            HttpHeaders oldCookies = cookiesFrom(loginResponse);

            // First refresh — creates new session in same family
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            // Get family ID before replay
            String familyId = jdbc.queryForObject(
                    "SELECT session_family_id::text FROM auth_session WHERE user_id = ?::uuid LIMIT 1",
                    String.class, userId);

            // Reuse old token — triggers replay
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            // All sessions in the family should be REVOKED
            Integer activeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE session_family_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, familyId);
            assertThat(activeCount).isEqualTo(0);

            List<String> revokeReasons = jdbc.queryForList(
                    "SELECT revoke_reason FROM auth_session WHERE session_family_id = ?::uuid AND status = 'REVOKED'",
                    String.class, familyId);
            assertThat(revokeReasons).contains("REFRESH_REPLAY_DETECTED");
        }

        @Test
        @DisplayName("new refresh token also fails after replay")
        void newTokenAlsoFailsAfterReplay() {
            var loginResponse = doLogin();
            HttpHeaders oldCookies = cookiesFrom(loginResponse);

            // First refresh — get new cookies
            var firstRefresh = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);
            HttpHeaders newCookies = cookiesFrom(firstRefresh);

            // Replay old token — revokes family
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            // New token should also fail (family revoked)
            ResponseEntity<Map> newTokenResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(newCookies), Map.class);

            assertThat(newTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("/me fails after replay with old access token")
        void meFailsAfterReplay() {
            var loginResponse = doLogin();
            HttpHeaders oldCookies = cookiesFrom(loginResponse);

            // Extract old access cookie
            String oldAccessCookie = loginResponse.getHeaders().get("Set-Cookie").stream()
                    .filter(c -> c.startsWith("MF_ACCESS="))
                    .map(c -> c.split(";")[0])
                    .findFirst().orElseThrow();

            // Refresh
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            // Replay old token
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            // Old access token should fail (session revoked)
            var meHeaders = new HttpHeaders();
            meHeaders.add("Cookie", oldAccessCookie);

            ResponseEntity<Map> meResponse = restTemplate.exchange(
                    "/auth/me", HttpMethod.GET,
                    new HttpEntity<>(meHeaders), Map.class);

            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("replay does not create new sessions")
        void replayDoesNotCreateNewSessions() {
            var loginResponse = doLogin();
            HttpHeaders oldCookies = cookiesFrom(loginResponse);

            // First refresh
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            Integer sessionsAfterRefresh = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid",
                    Integer.class, userId);

            // Replay
            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(oldCookies), Map.class);

            Integer sessionsAfterReplay = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid",
                    Integer.class, userId);

            assertThat(sessionsAfterReplay).isEqualTo(sessionsAfterRefresh);
        }
    }
}
