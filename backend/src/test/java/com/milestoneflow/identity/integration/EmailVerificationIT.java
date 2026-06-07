package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for email verification flow against PostgreSQL 17.
 */
class EmailVerificationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private Clock clock;

    @Autowired
    private EmailVerificationProperties properties;

    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("Email verification confirm")
    class Confirm {

        @Test
        @DisplayName("confirms valid token and activates user")
        void confirmsAndActivates() {
            // Setup: create user and token
            String rawToken = "valid-verification-token-" + uniqueSuffix;
            String tokenHash = tokenHasher.hash(rawToken);
            UUID userId = createTestUserWithToken(tokenHash);

            // Confirm
            ResponseEntity<Map> response = confirmToken(rawToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map body = response.getBody();
            Map data = (Map) body.get("data");
            assertThat(data.get("status")).isEqualTo("ACTIVE");

            // Verify DB state
            AppUser user = appUserRepository.findById(userId).orElseThrow();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getEmailVerifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("marks token as used")
        void marksTokenUsed() {
            String rawToken = "used-test-token-" + uniqueSuffix;
            String tokenHash = tokenHasher.hash(rawToken);
            UUID userId = createTestUserWithToken(tokenHash);

            confirmToken(rawToken);

            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects already used token")
        void rejectsUsedToken() {
            String rawToken = "double-use-token-" + uniqueSuffix;
            String tokenHash = tokenHasher.hash(rawToken);
            createTestUserWithToken(tokenHash);

            // First use succeeds
            confirmToken(rawToken);

            // Second use fails
            ResponseEntity<Map> response = confirmToken(rawToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("rejects expired token")
        void rejectsExpiredToken() {
            String rawToken = "expired-token-" + uniqueSuffix;
            String tokenHash = tokenHasher.hash(rawToken);

            UUID userId = idGenerator.nextId();
            AppUser user = AppUser.create(userId, "expired" + uniqueSuffix + "@test.com",
                    "expired" + uniqueSuffix + "@test.com", "Expired User",
                    passwordEncoder.encode("password123"), "en");
            appUserRepository.save(user);

            VerificationToken token = VerificationToken.create(
                    idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    tokenHash, Instant.parse("2020-01-01T00:00:00Z")); // expired
            verificationTokenRepository.save(token);

            ResponseEntity<Map> response = confirmToken(rawToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("rejects non-existent token")
        void rejectsNonExistentToken() {
            ResponseEntity<Map> response = confirmToken("nonexistent-token-value");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("does not activate DISABLED user")
        void doesNotActivateDisabledUser() {
            String rawToken = "disabled-token-" + uniqueSuffix;
            String tokenHash = tokenHasher.hash(rawToken);

            UUID userId = idGenerator.nextId();
            AppUser user = AppUser.create(userId, "disabled" + uniqueSuffix + "@test.com",
                    "disabled" + uniqueSuffix + "@test.com", "Disabled User",
                    passwordEncoder.encode("password123"), "en");
            user.disable();
            appUserRepository.save(user);

            VerificationToken token = VerificationToken.create(
                    idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    tokenHash, Instant.now(clock).plus(properties.tokenTtl()));
            verificationTokenRepository.save(token);

            ResponseEntity<Map> response = confirmToken(rawToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private UUID createTestUserWithToken(String tokenHash) {
        UUID userId = idGenerator.nextId();
        String email = "verify" + uniqueSuffix + UUID.randomUUID().toString().substring(0, 4) + "@test.com";
        AppUser user = AppUser.create(userId, email, email.toLowerCase(),
                "Verify User", passwordEncoder.encode("password123"), "en");
        appUserRepository.save(user);

        VerificationToken token = VerificationToken.create(
                idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                tokenHash, Instant.now(clock).plus(properties.tokenTtl()));
        verificationTokenRepository.save(token);

        return userId;
    }

    private ResponseEntity<Map> confirmToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return restTemplate.postForEntity(
                "/auth/email-verification/confirm",
                new HttpEntity<>(Map.of("token", token), headers),
                Map.class
        );
    }
}
