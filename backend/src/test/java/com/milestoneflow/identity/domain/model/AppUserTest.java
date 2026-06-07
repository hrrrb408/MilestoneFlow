package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AppUser} domain model.
 */
class AppUserTest {

    private static final UUID FIXED_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final String EMAIL = "user@example.test";
    private static final String EMAIL_NORM = "user@example.test";
    private static final String DISPLAY_NAME = "Test User";
    private static final String PASSWORD_HASH = "a".repeat(60); // bcrypt-like test hash
    private static final String LOCALE = "zh-TW";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T12:00:00Z");

    // ── Creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create user with valid parameters")
        void shouldCreateValidUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user.getId()).isEqualTo(FIXED_ID);
            assertThat(user.getEmail()).isEqualTo(EMAIL);
            assertThat(user.getEmailNormalized()).isEqualTo(EMAIL_NORM);
            assertThat(user.getDisplayName()).isEqualTo(DISPLAY_NAME);
            assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(user.getLocale()).isEqualTo(LOCALE);
        }

        @Test
        @DisplayName("should start in PENDING_VERIFICATION state")
        void shouldStartPendingVerification() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("should have null emailVerifiedAt initially")
        void shouldHaveNullEmailVerifiedAt() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user.getEmailVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("should have null lastLoginAt initially")
        void shouldHaveNullLastLoginAt() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user.getLastLoginAt()).isNull();
        }

        @Test
        @DisplayName("should reject null ID")
        void shouldRejectNullId() {
            assertThatThrownBy(() ->
                    AppUser.create(null, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("should reject null email")
        void shouldRejectNullEmail() {
            assertThatThrownBy(() ->
                    AppUser.create(FIXED_ID, null, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("email must not be null");
        }

        @Test
        @DisplayName("should reject null emailNormalized")
        void shouldRejectNullEmailNormalized() {
            assertThatThrownBy(() ->
                    AppUser.create(FIXED_ID, EMAIL, null, DISPLAY_NAME, PASSWORD_HASH, LOCALE)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("emailNormalized must not be null");
        }

        @Test
        @DisplayName("should reject null displayName")
        void shouldRejectNullDisplayName() {
            assertThatThrownBy(() ->
                    AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, null, PASSWORD_HASH, LOCALE)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("displayName must not be null");
        }

        @Test
        @DisplayName("should reject null passwordHash")
        void shouldRejectNullPasswordHash() {
            assertThatThrownBy(() ->
                    AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, null, LOCALE)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("passwordHash must not be null");
        }
    }

    // ── Email verification activation ────────────────────────────────────

    @Nested
    @DisplayName("Email verification activation")
    class EmailVerification {

        @Test
        @DisplayName("should activate PENDING_VERIFICATION user")
        void shouldActivatePendingUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            user.activateAfterEmailVerification(FIXED_INSTANT);

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getEmailVerifiedAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("should be idempotent for already ACTIVE user")
        void shouldBeIdempotentForActiveUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            user.activateAfterEmailVerification(FIXED_INSTANT);
            Instant originalVerifiedAt = user.getEmailVerifiedAt();

            user.activateAfterEmailVerification(FIXED_INSTANT.plusSeconds(60));

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getEmailVerifiedAt()).isEqualTo(originalVerifiedAt);
        }

        @Test
        @DisplayName("should reject DISABLED user")
        void shouldRejectDisabledUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            user.disable();

            assertThatThrownBy(() -> user.activateAfterEmailVerification(FIXED_INSTANT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DISABLED");
        }
    }

    // ── Disable ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Disable")
    class Disable {

        @Test
        @DisplayName("should disable ACTIVE user")
        void shouldDisableActiveUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            user.activateAfterEmailVerification(FIXED_INSTANT);

            user.disable();

            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("should disable PENDING_VERIFICATION user")
        void shouldDisablePendingUser() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            user.disable();

            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("should reject disabling already DISABLED user")
        void shouldRejectDoubleDisable() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            user.disable();

            assertThatThrownBy(user::disable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already DISABLED");
        }
    }

    // ── Successful login ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful login")
    class SuccessfulLogin {

        @Test
        @DisplayName("should record login timestamp")
        void shouldRecordLoginTimestamp() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            user.recordSuccessfulLogin(FIXED_INSTANT);

            assertThat(user.getLastLoginAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("should reject null loginAt")
        void shouldRejectNullLoginAt() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThatThrownBy(() -> user.recordSuccessfulLogin(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Password hash change ─────────────────────────────────────────────

    @Nested
    @DisplayName("Password hash change")
    class PasswordHashChange {

        @Test
        @DisplayName("should replace password hash")
        void shouldReplacePasswordHash() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            String newHash = "b".repeat(60);

            user.changePasswordHash(newHash);

            assertThat(user.getPasswordHash()).isEqualTo(newHash);
        }

        @Test
        @DisplayName("should reject null password hash")
        void shouldRejectNullNewHash() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThatThrownBy(() -> user.changePasswordHash(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── toString security ────────────────────────────────────────────────

    @Nested
    @DisplayName("toString security")
    class ToStringSecurity {

        @Test
        @DisplayName("should not include passwordHash in toString")
        void shouldNotIncludePasswordHash() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            String result = user.toString();

            assertThat(result).doesNotContain(PASSWORD_HASH);
        }
    }

    // ── Equality ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWithSameId() {
            AppUser user1 = AppUser.create(FIXED_ID, "a@test.com", "a@test.com", "A", "hash1", LOCALE);
            AppUser user2 = AppUser.create(FIXED_ID, "b@test.com", "b@test.com", "B", "hash2", "en-US");

            assertThat(user1).isEqualTo(user2);
            assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different IDs")
        void shouldNotBeEqualWithDifferentId() {
            AppUser user1 = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);
            AppUser user2 = AppUser.create(UUID.fromString("0191f5a0-5678-7abc-8def-0123456789ab"),
                    EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user).isNotEqualTo("not a user");
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            AppUser user = AppUser.create(FIXED_ID, EMAIL, EMAIL_NORM, DISPLAY_NAME, PASSWORD_HASH, LOCALE);

            assertThat(user).isEqualTo(user);
        }
    }
}
