package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.application.port.out.TokenHasher;
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
 * Integration tests for password reset flow against PostgreSQL 17.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Valid reset token successfully resets password</li>
 *   <li>Token is marked as used after successful reset</li>
 *   <li>All sessions are revoked with PASSWORD_RESET reason</li>
 *   <li>Old password cannot login after reset</li>
 *   <li>New password can login after reset</li>
 *   <li>Reuse of token fails with 401</li>
 *   <li>Expired token fails with 401</li>
 *   <li>Response does not contain token or hash</li>
 *   <li>Cookies are cleared after reset</li>
 * </ul>
 */
@DisplayName("Reset Password Flow IT")
class ResetPasswordFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenHasher tokenHasher;

    private static final String EMAIL = "reset-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String NEW_PASSWORD = "reset-new-password-456";
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
            """, EMAIL, EMAIL.toLowerCase(), "Reset Password IT User", encodedPassword);

        userId = jdbc.queryForObject("SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    private HttpHeaders doLoginAndGetCookies() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        var cookies = new HttpHeaders();
        loginResponse.getHeaders().get("Set-Cookie").forEach(c -> cookies.add("Cookie", c.split(";")[0]));
        return cookies;
    }

    /**
     * Generates a raw token, hashes it with TokenHasher, and inserts a PASSWORD_RESET
     * verification_token row. Returns the raw token for use in the reset request.
     */
    private String insertPasswordResetToken() {
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = tokenHasher.hash(rawToken);

        jdbc.update("""
            INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at, created_at)
            VALUES (gen_random_uuid(), ?::uuid, 'PASSWORD_RESET', ?, now() + interval '1 hour', now())
            """, userId, tokenHash);

        return rawToken;
    }

    /**
     * Inserts an expired PASSWORD_RESET token. Returns the raw token.
     */
    private String insertExpiredPasswordResetToken() {
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = tokenHasher.hash(rawToken);

        jdbc.update("""
            INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at, created_at)
            VALUES (gen_random_uuid(), ?::uuid, 'PASSWORD_RESET', ?, now() - interval '1 second', now() - interval '1 hour')
            """, userId, tokenHash);

        return rawToken;
    }

    private ResponseEntity<Map> doResetPassword(String rawToken, String newPassword) {
        var body = Map.of("token", rawToken, "newPassword", newPassword);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/auth/password/reset", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    @Nested
    @DisplayName("POST /auth/password/reset")
    class ResetPassword {

        @Test
        @DisplayName("reset succeeds with 200, token used_at set, password updated, sessions revoked")
        void resetSucceeds() {
            // Login to create a session
            doLoginAndGetCookies();

            // Insert reset token
            String rawToken = insertPasswordResetToken();

            ResponseEntity<Map> response = doResetPassword(rawToken, NEW_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Token should be marked as used
            Map<String, Object> tokenRow = jdbc.queryForMap(
                    "SELECT used_at FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET'",
                    userId);
            assertThat(tokenRow.get("used_at")).isNotNull();

            // Password should be updated
            String newHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, newHash)).isTrue();

            // Sessions should be revoked with PASSWORD_RESET reason
            Integer activeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, userId);
            assertThat(activeCount).isEqualTo(0);

            Integer revokedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?::uuid AND status = 'REVOKED' AND revoke_reason = 'PASSWORD_RESET'",
                    Integer.class, userId);
            assertThat(revokedCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("old password cannot login after reset")
        void oldPasswordCannotLogin() {
            String rawToken = insertPasswordResetToken();

            doResetPassword(rawToken, NEW_PASSWORD);

            var body = Map.of("email", EMAIL, "password", PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> oldLoginResponse = restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(oldLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("new password can login after reset")
        void newPasswordCanLogin() {
            String rawToken = insertPasswordResetToken();

            doResetPassword(rawToken, NEW_PASSWORD);

            var body = Map.of("email", EMAIL, "password", NEW_PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> newLoginResponse = restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("reuse of token fails with 401")
        void reuseTokenFails() {
            String rawToken = insertPasswordResetToken();

            // First use succeeds
            ResponseEntity<Map> firstResponse = doResetPassword(rawToken, NEW_PASSWORD);
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Second use fails
            ResponseEntity<Map> secondResponse = doResetPassword(rawToken, "another-password-789");
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) secondResponse.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_PASSWORD_RESET_TOKEN_INVALID");
        }

        @Test
        @DisplayName("expired token fails with 401")
        void expiredTokenFails() {
            String rawToken = insertExpiredPasswordResetToken();

            ResponseEntity<Map> response = doResetPassword(rawToken, NEW_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            @SuppressWarnings("unchecked")
            var errorBody = (Map<String, Object>) response.getBody();
            assertThat(errorBody.get("code")).isEqualTo("AUTH_PASSWORD_RESET_TOKEN_EXPIRED");

            // Password should NOT have changed
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);
            assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        }

        @Test
        @DisplayName("response does not contain token or hash")
        void responseDoesNotLeakTokenOrHash() {
            String rawToken = insertPasswordResetToken();

            ResponseEntity<Map> response = doResetPassword(rawToken, NEW_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            assertThat(data).doesNotContainKey("token");
            assertThat(data).doesNotContainKey("tokenHash");
            assertThat(data).doesNotContainKey("rawToken");
        }

        @Test
        @DisplayName("cookies cleared after reset")
        void cookiesClearedAfterReset() {
            // Login to get session cookies
            var loginCookies = doLoginAndGetCookies();

            String rawToken = insertPasswordResetToken();

            // Perform reset with session cookies attached
            var body = Map.of("token", rawToken, "newPassword", NEW_PASSWORD);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.putAll(loginCookies);

            ResponseEntity<Map> response = restTemplate.exchange("/auth/password/reset", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify cookies are cleared
            var setCookies = response.getHeaders().get("Set-Cookie");
            assertThat(setCookies).isNotNull();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_ACCESS="))).isTrue();
            assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_REFRESH="))).isTrue();
        }
    }
}
