package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VerificationToken} domain model.
 */
class VerificationTokenTest {

    private static final UUID FIXED_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final UUID USER_ID = UUID.fromString("0191f5a0-abcd-7abc-8def-0123456789ab");
    private static final String TOKEN_HASH = "c".repeat(64);
    private static final Instant EXPIRES_AT = Instant.parse("2026-06-02T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    // ── Creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create EMAIL_VERIFICATION token")
        void shouldCreateEmailVerificationToken() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.getId()).isEqualTo(FIXED_ID);
            assertThat(token.getUserId()).isEqualTo(USER_ID);
            assertThat(token.getPurpose()).isEqualTo(VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
        }

        @Test
        @DisplayName("should create PASSWORD_RESET token")
        void shouldCreatePasswordResetToken() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.PASSWORD_RESET, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.getPurpose()).isEqualTo(VerificationTokenPurpose.PASSWORD_RESET);
        }

        @Test
        @DisplayName("should have null usedAt initially")
        void shouldHaveNullUsedAt() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() {
            assertThatThrownBy(() ->
                    VerificationToken.create(null, USER_ID,
                            VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("should reject null userId")
        void shouldRejectNullUserId() {
            assertThatThrownBy(() ->
                    VerificationToken.create(FIXED_ID, null,
                            VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("userId must not be null");
        }

        @Test
        @DisplayName("should reject null purpose")
        void shouldRejectNullPurpose() {
            assertThatThrownBy(() ->
                    VerificationToken.create(FIXED_ID, USER_ID,
                            null, TOKEN_HASH, EXPIRES_AT)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("purpose must not be null");
        }

        @Test
        @DisplayName("should reject null tokenHash")
        void shouldRejectNullTokenHash() {
            assertThatThrownBy(() ->
                    VerificationToken.create(FIXED_ID, USER_ID,
                            VerificationTokenPurpose.EMAIL_VERIFICATION, null, EXPIRES_AT)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tokenHash must not be null");
        }

        @Test
        @DisplayName("should reject null expiresAt")
        void shouldRejectNullExpiresAt() {
            assertThatThrownBy(() ->
                    VerificationToken.create(FIXED_ID, USER_ID,
                            VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, null)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("expiresAt must not be null");
        }
    }

    // ── markUsed ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markUsed")
    class MarkUsed {

        @Test
        @DisplayName("should mark token as used")
        void shouldMarkUsed() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            token.markUsed(NOW);

            assertThat(token.getUsedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("should reject double use")
        void shouldRejectDoubleUse() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);
            token.markUsed(NOW);

            assertThatThrownBy(() -> token.markUsed(NOW.plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("should reject null usedAt")
        void shouldRejectNullUsedAt() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThatThrownBy(() -> token.markUsed(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Expiry ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("should not be expired before expiresAt")
        void shouldNotBeExpiredBeforeExpiry() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.isExpired(NOW)).isFalse();
        }

        @Test
        @DisplayName("should be expired at exact expiresAt")
        void shouldBeExpiredAtExpiry() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.isExpired(EXPIRES_AT)).isTrue();
        }

        @Test
        @DisplayName("should be expired after expiresAt")
        void shouldBeExpiredAfterExpiry() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
        }
    }

    // ── Usability ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Usability")
    class Usability {

        @Test
        @DisplayName("should be usable when not used and not expired")
        void shouldBeUsableWhenFresh() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.isUsable(NOW)).isTrue();
        }

        @Test
        @DisplayName("should not be usable when used")
        void shouldNotBeUsableWhenUsed() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);
            token.markUsed(NOW);

            assertThat(token.isUsable(NOW)).isFalse();
        }

        @Test
        @DisplayName("should not be usable when expired")
        void shouldNotBeUsableWhenExpired() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.isUsable(EXPIRES_AT)).isFalse();
        }
    }

    // ── toString security ────────────────────────────────────────────────

    @Nested
    @DisplayName("toString security")
    class ToStringSecurity {

        @Test
        @DisplayName("should not include token hash in toString")
        void shouldNotIncludeTokenHash() {
            VerificationToken token = VerificationToken.create(
                    FIXED_ID, USER_ID, VerificationTokenPurpose.EMAIL_VERIFICATION, TOKEN_HASH, EXPIRES_AT);

            assertThat(token.toString()).doesNotContain(TOKEN_HASH);
        }
    }
}
