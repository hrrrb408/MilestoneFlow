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
 * Integration tests for forgot-password flow against PostgreSQL 17.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>ACTIVE users receive a PASSWORD_RESET token</li>
 *   <li>EMAIL_VERIFICATION tokens are not affected</li>
 *   <li>Unknown emails return the same 200 response (anti-enumeration)</li>
 *   <li>Disabled users return the same 200 response and no token is created</li>
 *   <li>Response does not leak user status, token, or userId</li>
 *   <li>Multiple requests create multiple tokens (B1 §9.2)</li>
 * </ul>
 */
@DisplayName("Forgot Password Flow IT")
class ForgotPasswordFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "forgot-it@example.com";
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
            """, EMAIL, EMAIL.toLowerCase(), "Forgot Password IT User", encodedPassword);

        userId = jdbc.queryForObject("SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    private ResponseEntity<Map> doForgotPassword(String email) {
        var body = Map.of("email", email);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/auth/password/forgot", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    @Nested
    @DisplayName("POST /auth/password/forgot")
    class ForgotPassword {

        @Test
        @DisplayName("ACTIVE user forgot password returns 200 and creates PASSWORD_RESET token")
        void activeUserCreatesToken() {
            ResponseEntity<Map> response = doForgotPassword(EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            // Verify PASSWORD_RESET token was created
            Integer tokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET'",
                    Integer.class, userId);
            assertThat(tokenCount).isEqualTo(1);

            // Verify token_hash is 64 chars (SHA-256 hex)
            String tokenHash = jdbc.queryForObject(
                    "SELECT token_hash FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET' LIMIT 1",
                    String.class, userId);
            assertThat(tokenHash).hasSize(64);
        }

        @Test
        @DisplayName("EMAIL_VERIFICATION tokens not affected by forgot password")
        void emailVerificationTokensNotAffected() {
            // Insert an EMAIL_VERIFICATION token manually
            jdbc.update("""
                INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at, created_at)
                VALUES (gen_random_uuid(), ?::uuid, 'EMAIL_VERIFICATION', repeat('a', 64), now() + interval '24 hours', now())
                """, userId);

            Integer emailTokenCountBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid AND purpose = 'EMAIL_VERIFICATION'",
                    Integer.class, userId);
            assertThat(emailTokenCountBefore).isEqualTo(1);

            doForgotPassword(EMAIL);

            // EMAIL_VERIFICATION token should still be there, unchanged
            Integer emailTokenCountAfter = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid AND purpose = 'EMAIL_VERIFICATION'",
                    Integer.class, userId);
            assertThat(emailTokenCountAfter).isEqualTo(1);

            // PASSWORD_RESET token should also exist
            Integer resetTokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET'",
                    Integer.class, userId);
            assertThat(resetTokenCount).isEqualTo(1);
        }

        @Test
        @DisplayName("unknown email returns same 200 response (anti-enumeration)")
        void unknownEmailReturnsSameResponse() {
            ResponseEntity<Map> response = doForgotPassword("nonexistent@example.com");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            // No token created for unknown email
            Integer unknownUserTokens = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)",
                    Integer.class, "nonexistent@example.com");
            assertThat(unknownUserTokens).isEqualTo(0);
        }

        @Test
        @DisplayName("disabled user returns same 200 response and no token created")
        void disabledUserReturnsSameResponse() {
            String disabledEmail = "disabled-forgot-it@example.com";
            String encodedPw = passwordEncoder.encode(PASSWORD);
            jdbc.update("""
                INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, 'DISABLED', 'en', 0, now(), now())
                """, disabledEmail, disabledEmail.toLowerCase(), "Disabled Forgot User", encodedPw);

            String disabledUserId = jdbc.queryForObject(
                    "SELECT id::text FROM app_user WHERE email_normalized = ?",
                    String.class, disabledEmail.toLowerCase());

            ResponseEntity<Map> response = doForgotPassword(disabledEmail);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            // No token created for disabled user
            Integer tokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid",
                    Integer.class, disabledUserId);
            assertThat(tokenCount).isEqualTo(0);
        }

        @Test
        @DisplayName("forgot password response does not contain user status, token, or userId")
        void responseDoesNotLeakUserInfo() {
            ResponseEntity<Map> response = doForgotPassword(EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) response.getBody().get("data");

            // Response should only contain the accepted flag
            assertThat(data).doesNotContainKey("token");
            assertThat(data).doesNotContainKey("tokenHash");
            assertThat(data).doesNotContainKey("userId");
            assertThat(data).doesNotContainKey("status");
            assertThat(data).doesNotContainKey("email");
            assertThat(data).doesNotContainKey("resetUrl");
        }

        @Test
        @DisplayName("multiple forgot password requests create multiple tokens (B1 §9.2)")
        void multipleRequestsCreateMultipleTokens() {
            doForgotPassword(EMAIL);
            doForgotPassword(EMAIL);
            doForgotPassword(EMAIL);

            Integer tokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET'",
                    Integer.class, userId);
            assertThat(tokenCount).isEqualTo(3);
        }
    }
}
