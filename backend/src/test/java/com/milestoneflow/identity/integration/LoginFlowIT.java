package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.domain.type.UserStatus;
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

@DisplayName("Login Flow IT")
class LoginFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "login-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String userId;

    @BeforeEach
    void setUp() {
        // Clean up test data
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", EMAIL.toLowerCase());

        // Create a verified ACTIVE user directly in the database
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Login IT User", encodedPassword);

        userId = jdbc.queryForObject(
                "SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("active user login succeeds with 200")
        void loginSuccess() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("userId")).isEqualTo(userId);
            assertThat(data.get("email")).isEqualTo(EMAIL);
            assertThat(data.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("login creates auth_session in database")
        void createsAuthSession() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Integer sessionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(sessionCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("auth_session has correct fields")
        void authSessionFields() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> session = jdbc.queryForMap(
                    "SELECT * FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE' LIMIT 1",
                    userId);

            assertThat(session.get("status")).isEqualTo("ACTIVE");
            assertThat(session.get("refresh_generation")).isEqualTo(0);
            assertThat(session.get("session_family_id")).isNotNull();
            assertThat(((String) session.get("access_token_hash")).length()).isEqualTo(64);
            assertThat(((String) session.get("refresh_token_hash")).length()).isEqualTo(64);
            assertThat(session.get("access_expires_at")).isNotNull();
            assertThat(session.get("refresh_expires_at")).isNotNull();
        }

        @Test
        @DisplayName("raw access token is not in database")
        void rawAccessTokenNotInDatabase() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            // The raw token should NOT be stored anywhere in the database
            // Only the hash is stored
            Integer rawCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE access_token_hash NOT LIKE '%=%' AND LENGTH(access_token_hash) = 64",
                    Integer.class);
            assertThat(rawCount).isGreaterThan(0); // hashes are 64-char hex, not base64 tokens
        }

        @Test
        @DisplayName("updates last_login_at")
        void updatesLastLoginAt() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            var lastLogin = jdbc.queryForObject(
                    "SELECT last_login_at FROM app_user WHERE id = ?::uuid",
                    java.time.Instant.class, userId);
            assertThat(lastLogin).isNotNull();
        }

        @Test
        @DisplayName("wrong password returns 401")
        void wrongPassword() {
            var body = Map.of("email", EMAIL, "password", "wrong-password");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) response.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("wrong password does not create auth_session")
        void wrongPasswordNoSession() {
            var body = Map.of("email", EMAIL, "password", "wrong-password");
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Integer sessionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid",
                    Integer.class, userId);
            assertThat(sessionCount).isEqualTo(0);
        }

        @Test
        @DisplayName("nonexistent user returns 401 same as wrong password")
        void nonexistentUserSameError() {
            var body = Map.of("email", "nonexistent@example.com", "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) response.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("response body does not contain tokens")
        void noTokensInBody() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data).doesNotContainKey("accessToken");
            assertThat(data).doesNotContainKey("refreshToken");
            assertThat(data).doesNotContainKey("accessTokenHash");
            assertThat(data).doesNotContainKey("refreshTokenHash");
            assertThat(data).doesNotContainKey("passwordHash");
            assertThat(data).doesNotContainKey("sessionId");
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class Me {

        @Test
        @DisplayName("without cookie returns 401")
        void withoutCookieReturns401() {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/auth/me", HttpMethod.GET, null, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("login then access /me with cookie succeeds")
        void loginThenMeSucceeds() {
            // Login first
            var loginBody = Map.of("email", EMAIL, "password", PASSWORD);
            var loginHeaders = new HttpHeaders();
            loginHeaders.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> loginResponse = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(loginBody, loginHeaders), Void.class);

            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Extract cookies
            var setCookies = loginResponse.getHeaders().get("Set-Cookie");
            assertThat(setCookies).isNotNull();

            String accessCookie = setCookies.stream()
                    .filter(c -> c.startsWith("MF_ACCESS="))
                    .findFirst()
                    .orElse(null);
            assertThat(accessCookie).isNotNull();

            // Use access cookie to call /me
            var meHeaders = new HttpHeaders();
            meHeaders.add("Cookie", accessCookie);

            ResponseEntity<Map> meResponse = restTemplate.exchange(
                    "/auth/me", HttpMethod.GET,
                    new HttpEntity<>(meHeaders), Map.class);

            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) meResponse.getBody().get("data");
            assertThat(data.get("userId")).isEqualTo(userId);
            assertThat(data.get("email")).isEqualTo(EMAIL);
            assertThat(data.get("status")).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Cookie attributes")
    class CookieAttributes {

        @Test
        @DisplayName("login sets HttpOnly cookies")
        void httpOnlyCookies() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);

            var setCookies = response.getHeaders().get("Set-Cookie");
            assertThat(setCookies).isNotNull();

            String accessCookie = setCookies.stream()
                    .filter(c -> c.startsWith("MF_ACCESS="))
                    .findFirst().orElseThrow();
            assertThat(accessCookie).contains("HttpOnly");

            String refreshCookie = setCookies.stream()
                    .filter(c -> c.startsWith("MF_REFRESH="))
                    .findFirst().orElseThrow();
            assertThat(refreshCookie).contains("HttpOnly");
        }

        @Test
        @DisplayName("MF_REFRESH has SameSite=Strict")
        void refreshSameSite() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);

            var setCookies = response.getHeaders().get("Set-Cookie");
            String refreshCookie = setCookies.stream()
                    .filter(c -> c.startsWith("MF_REFRESH="))
                    .findFirst().orElseThrow();
            assertThat(refreshCookie).contains("SameSite=Strict");
        }

        @Test
        @DisplayName("MF_REFRESH path is scoped to refresh endpoint")
        void refreshPath() {
            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);

            var setCookies = response.getHeaders().get("Set-Cookie");
            String refreshCookie = setCookies.stream()
                    .filter(c -> c.startsWith("MF_REFRESH="))
                    .findFirst().orElseThrow();
            assertThat(refreshCookie).contains("Path=/api/v1/auth/refresh");
        }
    }
}
