package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link VerificationTokenRepository} against PostgreSQL 17.
 */
class VerificationTokenRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private static final Instant EXPIRES_AT = Instant.parse("2027-06-02T12:00:00Z");

    private UUID userId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = AppUser.create(UUID.randomUUID(), "token-" + uniqueSuffix + "@example.test",
                "token-" + uniqueSuffix + "@example.test", "Token User", "a".repeat(60), "zh-TW");
        appUserRepository.save(user);
        userId = user.getId();
    }

    private String hash(int seed) {
        return String.format("%064x", seed);
    }

    private VerificationToken createToken(UUID userId, VerificationTokenPurpose purpose, String tokenHash) {
        return VerificationToken.create(UUID.randomUUID(), userId, purpose, tokenHash, EXPIRES_AT);
    }

    // ── Save and retrieve ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Save and retrieve")
    class SaveAndRetrieve {

        @Test
        @DisplayName("should save and retrieve EMAIL_VERIFICATION token")
        void shouldSaveEmailVerificationToken() {
            VerificationToken token = createToken(userId, VerificationTokenPurpose.EMAIL_VERIFICATION, hash(1));
            verificationTokenRepository.save(token);

            VerificationToken loaded = verificationTokenRepository.findByTokenHash(hash(1)).orElseThrow();
            assertThat(loaded.getPurpose()).isEqualTo(VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(loaded.getUserId()).isEqualTo(userId);
            assertThat(loaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(loaded.getUsedAt()).isNull();
            assertThat(loaded.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should save and retrieve PASSWORD_RESET token")
        void shouldSavePasswordResetToken() {
            VerificationToken token = createToken(userId, VerificationTokenPurpose.PASSWORD_RESET, hash(2));
            verificationTokenRepository.save(token);

            VerificationToken loaded = verificationTokenRepository.findByTokenHash(hash(2)).orElseThrow();
            assertThat(loaded.getPurpose()).isEqualTo(VerificationTokenPurpose.PASSWORD_RESET);
        }

        @Test
        @DisplayName("should persist usedAt")
        void shouldPersistUsedAt() {
            VerificationToken token = createToken(userId, VerificationTokenPurpose.EMAIL_VERIFICATION, hash(3));
            Instant usedAt = Instant.parse("2026-06-01T14:00:00Z");
            token.markUsed(usedAt);
            verificationTokenRepository.save(token);

            VerificationToken loaded = verificationTokenRepository.findByTokenHash(hash(3)).orElseThrow();
            assertThat(loaded.getUsedAt()).isEqualTo(usedAt);
        }
    }

    // ── Token hash queries ────────────────────────────────────────────────

    @Nested
    @DisplayName("Token hash queries")
    class TokenHashQueries {

        @Test
        @DisplayName("should find by token hash")
        void shouldFindByTokenHash() {
            verificationTokenRepository.save(createToken(userId,
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(10)));

            assertThat(verificationTokenRepository.findByTokenHash(hash(10))).isPresent();
            assertThat(verificationTokenRepository.findByTokenHash("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("should find by token hash and purpose")
        void shouldFindByTokenHashAndPurpose() {
            verificationTokenRepository.save(createToken(userId,
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(11)));

            assertThat(verificationTokenRepository.findByTokenHashAndPurpose(
                    hash(11), VerificationTokenPurpose.EMAIL_VERIFICATION)).isPresent();
            assertThat(verificationTokenRepository.findByTokenHashAndPurpose(
                    hash(11), VerificationTokenPurpose.PASSWORD_RESET)).isEmpty();
        }
    }

    // ── User + Purpose query ──────────────────────────────────────────────

    @Nested
    @DisplayName("User and purpose queries")
    class UserPurposeQueries {

        @Test
        @DisplayName("should find by userId and purpose")
        void shouldFindByUserIdAndPurpose() {
            verificationTokenRepository.save(createToken(userId,
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(20)));
            verificationTokenRepository.save(createToken(userId,
                    VerificationTokenPurpose.PASSWORD_RESET, hash(21)));

            List<VerificationToken> emailTokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);

            assertThat(emailTokens).hasSize(1);
            assertThat(emailTokens.get(0).getPurpose()).isEqualTo(VerificationTokenPurpose.EMAIL_VERIFICATION);
        }
    }

    // ── Unique constraints ────────────────────────────────────────────────

    @Nested
    @DisplayName("Unique constraints")
    class UniqueConstraints {

        @Test
        @DisplayName("should reject duplicate token hash")
        void shouldRejectDuplicateTokenHash() {
            verificationTokenRepository.save(createToken(userId,
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(30)));

            assertThatThrownBy(() ->
                    verificationTokenRepository.save(createToken(userId,
                            VerificationTokenPurpose.PASSWORD_RESET, hash(30)))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── FK constraints ────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK constraints")
    class FkConstraints {

        @Test
        @DisplayName("should reject token with non-existent user")
        void shouldRejectNonExistentUser() {
            VerificationToken token = createToken(UUID.randomUUID(),
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(40));

            assertThatThrownBy(() -> verificationTokenRepository.save(token))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Expiry field ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expiry field")
    class ExpiryField {

        @Test
        @DisplayName("should persist expiresAt correctly")
        void shouldPersistExpiresAt() {
            VerificationToken token = createToken(userId,
                    VerificationTokenPurpose.EMAIL_VERIFICATION, hash(50));
            verificationTokenRepository.save(token);

            VerificationToken loaded = verificationTokenRepository.findByTokenHash(hash(50)).orElseThrow();
            assertThat(loaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
        }
    }
}
