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
 * Integration tests for password change flow against PostgreSQL 17.
 *
 * <p>Verifies that password change:
 * <ul>
 *   <li>Updates the password hash in the database</li>
 *   <li>Revokes all active sessions with PASSWORD_CHANGE reason</li>
 *   <li>Clears cookies</li>
 *   <li>Rejects wrong current password</li>
 *   <li>Enforces password policy on new password</li>
 * </ul>
 */
@DisplayName("Password Change Flow IT")
class PasswordChangeFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "pwchange-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String NEW_PASSWORD = "new-secure-password-456";
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
            """, EMAIL, EMAIL.toLowerCase(), "Password Change IT User", encodedPassword);

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

    private ResponseEntity<Map> doChangePassword(HttpHeaders cookies, String currentPw, String newPw) {
        var body = Map.of("currentPassword", currentPw, "newPassword", newPw);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.putAll(cookies);
        return restTemplate.exchange("/auth/password/change", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    @Nested
    @DisplayName("POST /auth/password/change")
    class ChangePassword {

        @Test
        @DisplayName("change password succeeds with 200 and cookies cleared")
        void changePasswordSucceeds() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> response = doChangePassword(cookies, PASSWORD, NEW_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            // Verify cookies are cleared
            var setCookies = response.getHeaders().get("Set-Cookie");
            assertThat(setCookies).isNotNull();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_ACCESS="))).isTrue();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_REFRESH="))).isTrue();
        }

        @Test
        @DisplayName("old password cannot login after change")
        void oldPasswordCannotLogin() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            doChangePassword(cookies, PASSWORD, NEW_PASSWORD);

            // Try login with old password
            var oldLoginBody = Map.of("email", EMAIL, "password", PASSWORD);
            var oldLoginHeaders = new HttpHeaders();
            oldLoginHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> oldLoginResponse = restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(oldLoginBody, oldLoginHeaders), Map.class);

            assertThat(oldLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("new password can login after change")
        void newPasswordCanLogin() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            doChangePassword(cookies, PASSWORD, NEW_PASSWORD);

            // Login with new password
            var newLoginBody = Map.of("email", EMAIL, "password", NEW_PASSWORD);
            var newLoginHeaders = new HttpHeaders();
            newLoginHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> newLoginResponse = restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(newLoginBody, newLoginHeaders), Map.class);

            assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("all sessions revoked after password change")
        void allSessionsRevoked() {
            // Create multiple sessions
            doLogin();
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            Integer activeBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(activeBefore).isGreaterThanOrEqualTo(2);

            doChangePassword(cookies, PASSWORD, NEW_PASSWORD);

            // All sessions should be REVOKED with PASSWORD_CHANGE reason
            Integer activeAfter = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(activeAfter).isEqualTo(0);

            Integer revokedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'REVOKED' AND revoke_reason = 'PASSWORD_CHANGE'",
                    Integer.class, userId);
            assertThat(revokedCount).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("password_hash changed in database")
        void passwordHashChanged() {
            String oldHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);

            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            doChangePassword(cookies, PASSWORD, NEW_PASSWORD);

            String newHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);

            assertThat(newHash).isNotEqualTo(oldHash);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, newHash)).isTrue();
            assertThat(passwordEncoder.matches(PASSWORD, newHash)).isFalse();
        }

        @Test
        @DisplayName("wrong current password returns 401 AUTH_INVALID_CREDENTIALS")
        void wrongCurrentPassword() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> response = doChangePassword(cookies, "wrong-password", NEW_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) response.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");

            // Password should NOT have changed
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);
            assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        }

        @Test
        @DisplayName("new password too short returns 422 AUTH_PASSWORD_POLICY_VIOLATION")
        void newPasswordTooShort() {
            var loginResponse = doLogin();
            var cookies = cookiesFrom(loginResponse);

            ResponseEntity<Map> response = doChangePassword(cookies, PASSWORD, "short");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) response.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_PASSWORD_POLICY_VIOLATION");

            // Password should NOT have changed
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);
            assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        }
    }
}
