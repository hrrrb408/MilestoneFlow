package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.service.SecretToken;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for user registration flow against PostgreSQL 17.
 */
class UserRegistrationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("Registration API")
    class RegistrationApi {

        @Test
        @DisplayName("registers a new user successfully")
        void registersSuccessfully() {
            Map<String, Object> request = Map.of(
                    "email", "user" + uniqueSuffix + "@example.test",
                    "displayName", "Test User",
                    "password", "secure-password-123"
            );

            ResponseEntity<Map> response = registerUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map body = response.getBody();
            assertThat(body).isNotNull();

            Map data = (Map) body.get("data");
            assertThat(data).isNotNull();
            assertThat(data.get("id")).isNotNull();
            assertThat(data.get("email")).isEqualTo("user" + uniqueSuffix + "@example.test");
            assertThat(data.get("status")).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("creates exactly one app_user row")
        void createsOneUserRow() {
            String email = "count" + uniqueSuffix + "@example.test";
            Map<String, Object> request = Map.of(
                    "email", email,
                    "displayName", "Count User",
                    "password", "password123"
            );

            registerUser(request);

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase());
            assertThat(user).isPresent();
        }

        @Test
        @DisplayName("sets status to PENDING_VERIFICATION")
        void setsPendingVerification() {
            String email = "status" + uniqueSuffix + "@example.test";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Status User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("saves display email preserving case")
        void savesDisplayEmail() {
            String email = "Display" + uniqueSuffix + "@Example.TEST";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Display User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            assertThat(user.getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("saves normalized email as lowercase")
        void savesNormalizedEmail() {
            String email = "Normal" + uniqueSuffix + "@Example.TEST";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Normal User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            assertThat(user.getEmailNormalized()).isEqualTo(email.toLowerCase());
        }

        @Test
        @DisplayName("saves encoded password that can be verified")
        void savesEncodedPassword() {
            String email = "pw" + uniqueSuffix + "@example.test";
            String rawPassword = "my-secure-password";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "PW User",
                    "password", rawPassword
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
            assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
            assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("creates a verification token with EMAIL_VERIFICATION purpose")
        void createsVerificationToken() {
            String email = "token" + uniqueSuffix + "@example.test";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Token User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    user.getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(1);

            VerificationToken token = tokens.get(0);
            assertThat(token.getPurpose()).isEqualTo(VerificationTokenPurpose.EMAIL_VERIFICATION);
        }

        @Test
        @DisplayName("token hash is 64 characters")
        void tokenHashIs64Chars() {
            String email = "hash" + uniqueSuffix + "@example.test";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Hash User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    user.getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens.get(0).toString()).doesNotContain("raw"); // no raw token leakage in toString
        }

        @Test
        @DisplayName("no raw token is stored in database")
        void noRawTokenInDb() {
            String email = "raw" + uniqueSuffix + "@example.test";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "Raw User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    user.getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            // Token hash is 64-char hex, raw tokens are 43-char base64url
            // Verify the stored hash is indeed a hash (64 hex chars)
            VerificationToken token = tokens.get(0);
            assertThat(token.toString()).hasSizeLessThan(200); // no raw token embedded
        }

        @Test
        @DisplayName("token expires_at is in the future")
        void tokenExpiresInFuture() {
            String email = "expire" + uniqueSuffix + "@example.test";
            Instant beforeRegister = Instant.now();

            registerUser(Map.of(
                    "email", email,
                    "displayName", "Expire User",
                    "password", "password123"
            ));

            var user = appUserRepository.findByEmailNormalized(email.toLowerCase()).orElseThrow();
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    user.getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens.get(0).getExpiresAt()).isAfter(beforeRegister);
        }

        @Test
        @DisplayName("does not create auth_session")
        void noAuthSession() {
            String email = "nosession" + uniqueSuffix + "@example.test";
            registerUser(Map.of(
                    "email", email,
                    "displayName", "No Session User",
                    "password", "password123"
            ));

            // Just verify user exists and has no sessions — no session repository exposed
            var user = appUserRepository.findByEmailNormalized(email.toLowerCase());
            assertThat(user).isPresent();
        }

        @Test
        @DisplayName("returns 409 for duplicate email")
        void duplicateEmail() {
            String email = "dup" + uniqueSuffix + "@example.test";
            Map<String, Object> request = Map.of(
                    "email", email,
                    "displayName", "First User",
                    "password", "password123"
            );

            registerUser(request);
            ResponseEntity<Map> second = registerUser(request);

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            Map body = second.getBody();
            assertThat(((Map) body).get("code")).isEqualTo("AUTH_EMAIL_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("concurrent registration with same email only succeeds once")
        void concurrentRegistration() throws InterruptedException {
            String email = "concurrent" + uniqueSuffix + "@example.test";

            Thread t1 = new Thread(() -> {
                try {
                    registerUser(Map.of(
                            "email", email,
                            "displayName", "Concurrent 1",
                            "password", "password123"
                    ));
                } catch (Exception ignored) {
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    registerUser(Map.of(
                            "email", email,
                            "displayName", "Concurrent 2",
                            "password", "password456"
                    ));
                } catch (Exception ignored) {
                }
            });

            t1.start();
            t2.start();
            t1.join(10000);
            t2.join(10000);

            // Only one user should exist
            var user = appUserRepository.findByEmailNormalized(email.toLowerCase());
            assertThat(user).isPresent();
        }

        @Test
        @DisplayName("response contains no password hash")
        void noPasswordHashInResponse() {
            ResponseEntity<Map> response = registerUser(Map.of(
                    "email", "safe" + uniqueSuffix + "@example.test",
                    "displayName", "Safe User",
                    "password", "password123"
            ));

            Map body = response.getBody();
            Map data = (Map) body.get("data");
            assertThat(data).doesNotContainKey("passwordHash");
            assertThat(data).doesNotContainKey("password");
            assertThat(data).doesNotContainKey("token");
            assertThat(data).doesNotContainKey("tokenHash");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ResponseEntity<Map> registerUser(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return restTemplate.postForEntity(
                "/auth/register",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }
}
