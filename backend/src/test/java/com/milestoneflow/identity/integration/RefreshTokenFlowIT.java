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
 * Integration tests for refresh token rotation flow against PostgreSQL 17.
 */
@DisplayName("Refresh Token Flow IT")
class RefreshTokenFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "refresh-flow-it@example.com";
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
            """, EMAIL, EMAIL.toLowerCase(), "Refresh IT User", encodedPassword);

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

    private String extractCookie(ResponseEntity<?> response, String name) {
        return response.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " cookie not found"));
    }

    private HttpHeaders cookiesFrom(ResponseEntity<?> response) {
        var headers = new HttpHeaders();
        response.getHeaders().get("Set-Cookie").forEach(c -> headers.add("Cookie", c.split(";")[0]));
        return headers;
    }

    @Nested
    @DisplayName("POST /auth/refresh — success")
    class RefreshSuccess {

        @Test
        @DisplayName("refresh after login returns 200")
        void refreshReturns200() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("refresh returns authenticated=true")
        void refreshReturnsAuthenticated() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) refreshResponse.getBody().get("data");
            assertThat(data.get("authenticated")).isEqualTo(true);
        }

        @Test
        @DisplayName("refresh sets new MF_ACCESS cookie")
        void refreshSetsNewAccessCookie() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Void> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            String accessCookie = extractCookie(refreshResponse, "MF_ACCESS");
            assertThat(accessCookie).contains("HttpOnly");
        }

        @Test
        @DisplayName("refresh sets new MF_REFRESH cookie")
        void refreshSetsNewRefreshCookie() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Void> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Void.class);

            String refreshCookie = extractCookie(refreshResponse, "MF_REFRESH");
            assertThat(refreshCookie).contains("HttpOnly");
            assertThat(refreshCookie).contains("SameSite=Strict");
            assertThat(refreshCookie).contains("Path=/api/v1/auth/refresh");
        }

        @Test
        @DisplayName("response body does not contain tokens")
        void responseBodyNoTokens() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> refreshResponse = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) refreshResponse.getBody().get("data");
            assertThat(data).doesNotContainKey("accessToken");
            assertThat(data).doesNotContainKey("refreshToken");
            assertThat(data).doesNotContainKey("accessTokenHash");
            assertThat(data).doesNotContainKey("refreshTokenHash");
            assertThat(data).doesNotContainKey("sessionId");
            assertThat(data).doesNotContainKey("sessionFamilyId");
        }

        @Test
        @DisplayName("old session is REVOKED with REFRESH_ROTATED reason")
        void oldSessionRevoked() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            // Get the old session ID
            String oldSessionId = jdbc.queryForObject(
                    "SELECT id::text FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE' LIMIT 1",
                    String.class, userId);

            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            Map<String, Object> oldSession = jdbc.queryForMap(
                    "SELECT status, revoke_reason FROM auth_session WHERE id = ?::uuid",
                    oldSessionId);
            assertThat(oldSession.get("status")).isEqualTo("REVOKED");
            assertThat(oldSession.get("revoke_reason")).isEqualTo("REFRESH_ROTATED");
        }

        @Test
        @DisplayName("new session is ACTIVE in same family")
        void newSessionSameFamily() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            String oldFamilyId = jdbc.queryForObject(
                    "SELECT session_family_id::text FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    String.class, userId);

            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            Map<String, Object> newSession = jdbc.queryForMap(
                    "SELECT * FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    userId);
            assertThat(newSession.get("session_family_id")).isEqualTo(oldFamilyId);
            assertThat(newSession.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("new session has generation + 1")
        void newSessionGenerationIncremented() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            Map<String, Object> newSession = jdbc.queryForMap(
                    "SELECT refresh_generation FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    userId);
            assertThat(newSession.get("refresh_generation")).isEqualTo(1L);
        }

        @Test
        @DisplayName("new session has different token hashes")
        void newSessionDifferentHashes() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            Map<String, Object> oldSession = jdbc.queryForMap(
                    "SELECT access_token_hash, refresh_token_hash FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    userId);

            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            Map<String, Object> newSession = jdbc.queryForMap(
                    "SELECT access_token_hash, refresh_token_hash FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    userId);

            assertThat(newSession.get("access_token_hash")).isNotEqualTo(oldSession.get("access_token_hash"));
            assertThat(newSession.get("refresh_token_hash")).isNotEqualTo(oldSession.get("refresh_token_hash"));
        }

        @Test
        @DisplayName("raw tokens are not in database")
        void rawTokensNotInDatabase() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            // All stored hashes should be 64-char hex (SHA-256), not raw tokens
            List<String> hashes = jdbc.queryForList(
                    "SELECT access_token_hash FROM auth_session WHERE user_id = ?::uuid",
                    String.class, userId);
            for (String hash : hashes) {
                assertThat(hash).hasSize(64);
                assertThat(hash).matches("[0-9a-f]+");
            }
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh — errors")
    class RefreshErrors {

        @Test
        @DisplayName("no refresh cookie returns 401")
        void noRefreshCookie() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST, null, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().get("code")).isEqualTo("AUTH_UNAUTHENTICATED");
        }

        @Test
        @DisplayName("invalid refresh token returns 401")
        void invalidRefreshToken() {
            var headers = new HttpHeaders();
            headers.add("Cookie", "MF_REFRESH=invalid-token-value");

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("expired refresh token returns 401")
        void expiredRefreshToken() {
            var loginResponse = doLogin();

            // Get session and update refresh_expires_at to the past
            jdbc.update("""
                UPDATE auth_session SET refresh_expires_at = now() - INTERVAL '1 day'
                WHERE user_id = ?::uuid AND status = 'ACTIVE'
                """, userId);

            var cookies = cookiesFrom(loginResponse);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(cookies), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_EXPIRED");
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh — security")
    class RefreshSecurity {

        @Test
        @DisplayName("refresh endpoint does not accept body token")
        void doesNotAcceptBodyToken() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            loginResponse.getHeaders().get("Set-Cookie")
                    .forEach(c -> headers.add("Cookie", c.split(";")[0]));

            // Body should be ignored — refresh still works with valid cookie
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(Map.of("refreshToken", "body-token"), headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("refresh endpoint does not accept query param token")
        void doesNotAcceptQueryParamToken() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/refresh?refreshToken=query-token", HttpMethod.POST,
                    null, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("refresh cookie cannot authenticate /me")
        void refreshCookieCannotAuthMe() {
            var loginResponse = doLogin();
            // Extract only MF_REFRESH cookie
            String refreshCookie = loginResponse.getHeaders().get("Set-Cookie").stream()
                    .filter(c -> c.startsWith("MF_REFRESH="))
                    .map(c -> c.split(";")[0])
                    .findFirst().orElseThrow();

            var headers = new HttpHeaders();
            headers.add("Cookie", refreshCookie);

            ResponseEntity<Map> meResponse = restTemplate.exchange(
                    "/auth/me", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            // MF_REFRESH has path=/api/v1/auth/refresh, so it won't even be sent to /me
            // But even if it were, the auth filter only reads MF_ACCESS
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
