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
import com.milestoneflow.shared.testing.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for email verification flow against PostgreSQL 17.
 *
 * <p>Uses a {@link MutableClock} so that time-dependent tests (e.g., token expiry)
 * are fully deterministic without relying on {@code Thread.sleep}.
 */
@ContextConfiguration(classes = EmailVerificationIT.ClockConfig.class)
class EmailVerificationIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class ClockConfig {

        private static final Instant BASE_TIME = Instant.parse("2026-06-07T12:00:00Z");

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE_TIME);
        }
    }

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
    private MutableClock mutableClock;

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
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

            // Token expires 1 second from current clock time.
            // ck_verification_token_expiry requires expires_at > created_at;
            // both timestamps use the same MutableClock, so the constraint is satisfied.
            VerificationToken token = VerificationToken.create(
                    idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    tokenHash, Instant.now(mutableClock).plusSeconds(1));
            verificationTokenRepository.save(token);

            // Advance clock past expiry — fully deterministic, no Thread.sleep
            mutableClock.advance(Duration.ofSeconds(2));

            ResponseEntity<Map> response = confirmToken(rawToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // Verify user is still PENDING_VERIFICATION
            AppUser loadedUser = appUserRepository.findById(userId).orElseThrow();
            assertThat(loadedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(loadedUser.getEmailVerifiedAt()).isNull();

            // Verify token is still unused
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).getUsedAt()).isNull();
        }

        @Test
        @DisplayName("rejects non-existent token")
        void rejectsNonExistentToken() {
            ResponseEntity<Map> response = confirmToken("nonexistent-token-value");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
                    tokenHash, Instant.now(mutableClock).plus(properties.tokenTtl()));
            verificationTokenRepository.save(token);

            ResponseEntity<Map> response = confirmToken(rawToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
                tokenHash, Instant.now(mutableClock).plus(properties.tokenTtl()));
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
