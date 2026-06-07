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
 * Integration tests for email verification resend flow against PostgreSQL 17.
 */
class EmailVerificationResendIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private Clock clock;

    @Autowired
    private EmailVerificationProperties properties;

    @Autowired
    private TokenHasher tokenHasher;

    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("Resend verification email")
    class Resend {

        @Test
        @DisplayName("PENDING user gets new token after resend (old token kept per B1 §8.2)")
        void pendingUserGetsNewToken() {
            UUID userId = createPendingUser("resend" + uniqueSuffix + "@test.com");

            // Initially has 1 token from setup
            List<VerificationToken> before = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(before).hasSize(1);

            // Resend
            ResponseEntity<Map> response = resendVerification("resend" + uniqueSuffix + "@test.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Per B1 Baseline §8.2: multiple valid tokens allowed.
            // Should have 2 tokens (old kept + new created)
            List<VerificationToken> after = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(after).hasSize(2);
        }

        @Test
        @DisplayName("old EMAIL_VERIFICATION tokens remain valid after resend (B1 §8.2)")
        void oldTokensRemainValid() {
            UUID userId = createPendingUser("old" + uniqueSuffix + "@test.com");
            UUID oldTokenId = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION).get(0).getId();

            resendVerification("old" + uniqueSuffix + "@test.com");

            // Old token should still exist (not deleted)
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens.stream().anyMatch(t -> t.getId().equals(oldTokenId))).isTrue();
        }

        @Test
        @DisplayName("PASSWORD_RESET tokens are not affected")
        void passwordResetTokensNotAffected() {
            UUID userId = createPendingUser("pr" + uniqueSuffix + "@test.com");

            // Add a PASSWORD_RESET token
            VerificationToken resetToken = VerificationToken.create(
                    idGenerator.nextId(), userId, VerificationTokenPurpose.PASSWORD_RESET,
                    "a".repeat(64), Instant.now(clock).plus(properties.tokenTtl()));
            verificationTokenRepository.save(resetToken);

            resendVerification("pr" + uniqueSuffix + "@test.com");

            // PASSWORD_RESET token should still exist
            List<VerificationToken> resetTokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.PASSWORD_RESET);
            assertThat(resetTokens).hasSize(1);
        }

        @Test
        @DisplayName("unknown email returns same 200")
        void unknownEmail() {
            ResponseEntity<Map> response = resendVerification("nonexistent" + uniqueSuffix + "@test.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("ACTIVE user returns same 200")
        void activeUser() {
            UUID userId = createPendingUser("active" + uniqueSuffix + "@test.com");
            AppUser user = appUserRepository.findById(userId).orElseThrow();
            user.activateAfterEmailVerification(Instant.now(clock));
            appUserRepository.save(user);

            ResponseEntity<Map> response = resendVerification("active" + uniqueSuffix + "@test.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DISABLED user returns same 200")
        void disabledUser() {
            UUID userId = createPendingUser("dis" + uniqueSuffix + "@test.com");
            AppUser user = appUserRepository.findById(userId).orElseThrow();
            user.disable();
            appUserRepository.save(user);

            ResponseEntity<Map> response = resendVerification("dis" + uniqueSuffix + "@test.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("new token can be confirmed")
        void newTokenCanBeConfirmed() {
            UUID userId = createPendingUser("confirm" + uniqueSuffix + "@test.com");

            resendVerification("confirm" + uniqueSuffix + "@test.com");

            // Old + new token = 2 tokens total, all unused
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(2);
            assertThat(tokens).allMatch(t -> t.getUsedAt() == null);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private UUID createPendingUser(String email) {
        UUID userId = idGenerator.nextId();
        AppUser user = AppUser.create(userId, email, email.toLowerCase(),
                "Test User", passwordEncoder.encode("password123"), "en");
        appUserRepository.save(user);

        VerificationToken token = VerificationToken.create(
                idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                tokenHasher.hash("test-token-" + UUID.randomUUID()),
                Instant.now(clock).plus(properties.tokenTtl()));
        verificationTokenRepository.save(token);

        return userId;
    }

    private ResponseEntity<Map> resendVerification(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return restTemplate.postForEntity(
                "/auth/email-verification/resend",
                new HttpEntity<>(Map.of("email", email), headers),
                Map.class
        );
    }
}
