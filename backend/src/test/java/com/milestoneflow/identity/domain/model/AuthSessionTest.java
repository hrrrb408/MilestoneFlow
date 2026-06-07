package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuthSession} domain model.
 */
class AuthSessionTest {

    private static final UUID FIXED_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final UUID USER_ID = UUID.fromString("0191f5a0-abcd-7abc-8def-0123456789ab");
    private static final UUID FAMILY_ID = UUID.fromString("0191f5a0-ef01-7abc-8def-0123456789ab");
    private static final String ACCESS_HASH = "a".repeat(64);
    private static final String REFRESH_HASH = "b".repeat(64);
    private static final Instant ACCESS_EXPIRES = Instant.parse("2026-06-01T12:15:00Z");
    private static final Instant REFRESH_EXPIRES = Instant.parse("2026-07-01T12:00:00Z");

    private AuthSession createDefaultSession() {
        return AuthSession.create(FIXED_ID, USER_ID, ACCESS_HASH, REFRESH_HASH,
                FAMILY_ID, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, "Mozilla/5.0", "192.168.1.1");
    }

    // ── Creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create session with valid parameters")
        void shouldCreateValidSession() {
            AuthSession session = createDefaultSession();

            assertThat(session.getId()).isEqualTo(FIXED_ID);
            assertThat(session.getUserId()).isEqualTo(USER_ID);
            assertThat(session.getSessionFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(session.getRefreshGeneration()).isEqualTo(0);
            assertThat(session.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(session.getIpAddress()).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("should start in ACTIVE state")
        void shouldStartActive() {
            AuthSession session = createDefaultSession();

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("should have null lastUsedAt initially")
        void shouldHaveNullLastUsedAt() {
            AuthSession session = createDefaultSession();

            assertThat(session.getLastUsedAt()).isNull();
        }

        @Test
        @DisplayName("should have null revokedAt initially")
        void shouldHaveNullRevokedAt() {
            AuthSession session = createDefaultSession();

            assertThat(session.getRevokedAt()).isNull();
        }

        @Test
        @DisplayName("should have null revokeReason initially")
        void shouldHaveNullRevokeReason() {
            AuthSession session = createDefaultSession();

            assertThat(session.getRevokeReason()).isNull();
        }

        @Test
        @DisplayName("should reject null userId")
        void shouldRejectNullUserId() {
            assertThatThrownBy(() ->
                    AuthSession.create(FIXED_ID, null, ACCESS_HASH, REFRESH_HASH,
                            FAMILY_ID, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("userId must not be null");
        }

        @Test
        @DisplayName("should reject null accessTokenHash")
        void shouldRejectNullAccessTokenHash() {
            assertThatThrownBy(() ->
                    AuthSession.create(FIXED_ID, USER_ID, null, REFRESH_HASH,
                            FAMILY_ID, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accessTokenHash must not be null");
        }

        @Test
        @DisplayName("should reject null refreshTokenHash")
        void shouldRejectNullRefreshTokenHash() {
            assertThatThrownBy(() ->
                    AuthSession.create(FIXED_ID, USER_ID, ACCESS_HASH, null,
                            FAMILY_ID, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("refreshTokenHash must not be null");
        }

        @Test
        @DisplayName("should reject null sessionFamilyId")
        void shouldRejectNullSessionFamilyId() {
            assertThatThrownBy(() ->
                    AuthSession.create(FIXED_ID, USER_ID, ACCESS_HASH, REFRESH_HASH,
                            null, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null)
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sessionFamilyId must not be null");
        }

        @Test
        @DisplayName("should reject negative refreshGeneration")
        void shouldRejectNegativeGeneration() {
            assertThatThrownBy(() ->
                    AuthSession.create(FIXED_ID, USER_ID, ACCESS_HASH, REFRESH_HASH,
                            FAMILY_ID, -1, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null)
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refreshGeneration must not be negative");
        }

        @Test
        @DisplayName("should allow null userAgent and ipAddress")
        void shouldAllowNullUserAgentAndIp() {
            AuthSession session = AuthSession.create(FIXED_ID, USER_ID, ACCESS_HASH, REFRESH_HASH,
                    FAMILY_ID, 0, ACCESS_EXPIRES, REFRESH_EXPIRES, null, null);

            assertThat(session.getUserAgent()).isNull();
            assertThat(session.getIpAddress()).isNull();
        }
    }

    // ── markUsed ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markUsed")
    class MarkUsed {

        @Test
        @DisplayName("should update lastUsedAt")
        void shouldUpdateLastUsedAt() {
            AuthSession session = createDefaultSession();
            Instant usedAt = Instant.parse("2026-06-01T12:05:00Z");

            session.markUsed(usedAt);

            assertThat(session.getLastUsedAt()).isEqualTo(usedAt);
        }

        @Test
        @DisplayName("should reject null usedAt")
        void shouldRejectNullUsedAt() {
            AuthSession session = createDefaultSession();

            assertThatThrownBy(() -> session.markUsed(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Revoke ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Revoke")
    class Revoke {

        @Test
        @DisplayName("should revoke ACTIVE session")
        void shouldRevokeActiveSession() {
            AuthSession session = createDefaultSession();
            Instant revokedAt = Instant.parse("2026-06-01T13:00:00Z");

            session.revoke(revokedAt, "user_logout");

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(session.getRevokedAt()).isEqualTo(revokedAt);
            assertThat(session.getRevokeReason()).isEqualTo("user_logout");
        }

        @Test
        @DisplayName("should allow revoke without reason")
        void shouldAllowRevokeWithoutReason() {
            AuthSession session = createDefaultSession();

            session.revoke(Instant.parse("2026-06-01T13:00:00Z"), null);

            assertThat(session.getRevokeReason()).isNull();
        }

        @Test
        @DisplayName("should be idempotent for already REVOKED session")
        void shouldBeIdempotentForRevokedSession() {
            AuthSession session = createDefaultSession();
            session.revoke(Instant.parse("2026-06-01T13:00:00Z"), "first");
            Instant originalRevokedAt = session.getRevokedAt();

            session.revoke(Instant.parse("2026-06-01T14:00:00Z"), "second");

            assertThat(session.getRevokedAt()).isEqualTo(originalRevokedAt);
            assertThat(session.getRevokeReason()).isEqualTo("first");
        }

        @Test
        @DisplayName("should reject revoking EXPIRED session")
        void shouldRejectRevokingExpiredSession() {
            AuthSession session = createDefaultSession();
            session.markExpired(REFRESH_EXPIRES);

            assertThatThrownBy(() -> session.revoke(Instant.parse("2026-06-01T14:00:00Z"), "late"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EXPIRED");
        }
    }

    // ── Expiry checks ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expiry checks")
    class ExpiryChecks {

        @Test
        @DisplayName("should detect access token not expired")
        void shouldDetectAccessNotExpired() {
            AuthSession session = createDefaultSession();

            assertThat(session.isAccessExpired(Instant.parse("2026-06-01T12:10:00Z"))).isFalse();
        }

        @Test
        @DisplayName("should detect access token expired at exact time")
        void shouldDetectAccessExpiredAtExactTime() {
            AuthSession session = createDefaultSession();

            assertThat(session.isAccessExpired(ACCESS_EXPIRES)).isTrue();
        }

        @Test
        @DisplayName("should detect access token expired after time")
        void shouldDetectAccessExpiredAfterTime() {
            AuthSession session = createDefaultSession();

            assertThat(session.isAccessExpired(Instant.parse("2026-06-01T12:20:00Z"))).isTrue();
        }

        @Test
        @DisplayName("should detect refresh token not expired")
        void shouldDetectRefreshNotExpired() {
            AuthSession session = createDefaultSession();

            assertThat(session.isRefreshExpired(Instant.parse("2026-06-15T12:00:00Z"))).isFalse();
        }

        @Test
        @DisplayName("should detect refresh token expired at exact time")
        void shouldDetectRefreshExpiredAtExactTime() {
            AuthSession session = createDefaultSession();

            assertThat(session.isRefreshExpired(REFRESH_EXPIRES)).isTrue();
        }

        @Test
        @DisplayName("should detect refresh token expired after time")
        void shouldDetectRefreshExpiredAfterTime() {
            AuthSession session = createDefaultSession();

            assertThat(session.isRefreshExpired(Instant.parse("2026-08-01T12:00:00Z"))).isTrue();
        }
    }

    // ── markExpired ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("markExpired")
    class MarkExpired {

        @Test
        @DisplayName("should mark ACTIVE session as EXPIRED when refresh expired")
        void shouldMarkExpired() {
            AuthSession session = createDefaultSession();

            session.markExpired(REFRESH_EXPIRES);

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.EXPIRED);
        }

        @Test
        @DisplayName("should reject marking non-ACTIVE session")
        void shouldRejectNonActive() {
            AuthSession session = createDefaultSession();
            session.revoke(Instant.parse("2026-06-01T13:00:00Z"), "test");

            assertThatThrownBy(() -> session.markExpired(REFRESH_EXPIRES))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should reject when not actually expired")
        void shouldRejectWhenNotExpired() {
            AuthSession session = createDefaultSession();

            assertThatThrownBy(() -> session.markExpired(Instant.parse("2026-06-01T12:10:00Z")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not expired");
        }
    }

    // ── Refresh rotation behaviour ────────────────────────────────────────

    @Nested
    @DisplayName("revokeAsRotated")
    class RevokeAsRotated {

        @Test
        @DisplayName("should revoke ACTIVE session as REFRESH_ROTATED")
        void shouldRevokeAsRotated() {
            AuthSession session = createDefaultSession();
            Instant revokedAt = Instant.parse("2026-06-01T13:00:00Z");

            session.revokeAsRotated(revokedAt);

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(session.getRevokedAt()).isEqualTo(revokedAt);
            assertThat(session.getRevokeReason()).isEqualTo(AuthSessionRevokeReason.REFRESH_ROTATED);
        }

        @Test
        @DisplayName("should be idempotent for already ROTATED session")
        void shouldBeIdempotentForRotatedSession() {
            AuthSession session = createDefaultSession();
            session.revokeAsRotated(Instant.parse("2026-06-01T13:00:00Z"));
            Instant originalRevokedAt = session.getRevokedAt();

            session.revokeAsRotated(Instant.parse("2026-06-01T14:00:00Z"));

            assertThat(session.getRevokedAt()).isEqualTo(originalRevokedAt);
        }

        @Test
        @DisplayName("should reject rotating EXPIRED session")
        void shouldRejectExpiredSession() {
            AuthSession session = createDefaultSession();
            session.markExpired(REFRESH_EXPIRES);

            assertThatThrownBy(() -> session.revokeAsRotated(Instant.parse("2026-06-01T14:00:00Z")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only ACTIVE sessions can be revoked as rotated");
        }

        @Test
        @DisplayName("should reject rotating REVOKED (non-rotated) session")
        void shouldRejectRevokedNonRotatedSession() {
            AuthSession session = createDefaultSession();
            session.revoke(Instant.parse("2026-06-01T13:00:00Z"), "USER_LOGOUT");

            assertThatThrownBy(() -> session.revokeAsRotated(Instant.parse("2026-06-01T14:00:00Z")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should reject null revokedAt")
        void shouldRejectNullRevokedAt() {
            AuthSession session = createDefaultSession();

            assertThatThrownBy(() -> session.revokeAsRotated(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("revokeAsReplayDetected")
    class RevokeAsReplayDetected {

        @Test
        @DisplayName("should revoke ACTIVE session as REPLAY_DETECTED")
        void shouldRevokeActiveAsReplayDetected() {
            AuthSession session = createDefaultSession();
            Instant revokedAt = Instant.parse("2026-06-01T13:00:00Z");

            session.revokeAsReplayDetected(revokedAt);

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(session.getRevokedAt()).isEqualTo(revokedAt);
            assertThat(session.getRevokeReason()).isEqualTo(AuthSessionRevokeReason.REFRESH_REPLAY_DETECTED);
        }

        @Test
        @DisplayName("should revoke REVOKED (rotated) session as REPLAY_DETECTED")
        void shouldRevokeRotatedAsReplayDetected() {
            AuthSession session = createDefaultSession();
            session.revokeAsRotated(Instant.parse("2026-06-01T13:00:00Z"));

            session.revokeAsReplayDetected(Instant.parse("2026-06-01T14:00:00Z"));

            assertThat(session.getRevokeReason()).isEqualTo(AuthSessionRevokeReason.REFRESH_REPLAY_DETECTED);
        }

        @Test
        @DisplayName("should revoke EXPIRED session as REPLAY_DETECTED")
        void shouldRevokeExpiredAsReplayDetected() {
            AuthSession session = createDefaultSession();
            session.markExpired(REFRESH_EXPIRES);

            session.revokeAsReplayDetected(Instant.parse("2026-07-02T00:00:00Z"));

            assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(session.getRevokeReason()).isEqualTo(AuthSessionRevokeReason.REFRESH_REPLAY_DETECTED);
        }

        @Test
        @DisplayName("should be idempotent for already REPLAY_DETECTED session")
        void shouldBeIdempotentForReplayDetected() {
            AuthSession session = createDefaultSession();
            session.revokeAsReplayDetected(Instant.parse("2026-06-01T13:00:00Z"));
            Instant originalRevokedAt = session.getRevokedAt();

            session.revokeAsReplayDetected(Instant.parse("2026-06-01T14:00:00Z"));

            assertThat(session.getRevokedAt()).isEqualTo(originalRevokedAt);
        }

        @Test
        @DisplayName("should reject null revokedAt")
        void shouldRejectNullRevokedAt() {
            AuthSession session = createDefaultSession();

            assertThatThrownBy(() -> session.revokeAsReplayDetected(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isRefreshRotated")
    class IsRefreshRotated {

        @Test
        @DisplayName("should return false for ACTIVE session")
        void shouldReturnFalseForActive() {
            AuthSession session = createDefaultSession();

            assertThat(session.isRefreshRotated()).isFalse();
        }

        @Test
        @DisplayName("should return true after revokeAsRotated")
        void shouldReturnTrueAfterRotated() {
            AuthSession session = createDefaultSession();
            session.revokeAsRotated(Instant.parse("2026-06-01T13:00:00Z"));

            assertThat(session.isRefreshRotated()).isTrue();
        }

        @Test
        @DisplayName("should return false for REVOKED with different reason")
        void shouldReturnFalseForOtherReason() {
            AuthSession session = createDefaultSession();
            session.revoke(Instant.parse("2026-06-01T13:00:00Z"), "USER_LOGOUT");

            assertThat(session.isRefreshRotated()).isFalse();
        }

        @Test
        @DisplayName("should return false for EXPIRED session")
        void shouldReturnFalseForExpired() {
            AuthSession session = createDefaultSession();
            session.markExpired(REFRESH_EXPIRES);

            assertThat(session.isRefreshRotated()).isFalse();
        }

        @Test
        @DisplayName("should return false after replay detection")
        void shouldReturnFalseAfterReplayDetection() {
            AuthSession session = createDefaultSession();
            session.revokeAsRotated(Instant.parse("2026-06-01T13:00:00Z"));
            session.revokeAsReplayDetected(Instant.parse("2026-06-01T13:30:00Z"));

            assertThat(session.isRefreshRotated()).isFalse();
        }
    }

    // ── toString security ────────────────────────────────────────────────

    @Nested
    @DisplayName("toString security")
    class ToStringSecurity {

        @Test
        @DisplayName("should not include access token hash in toString")
        void shouldNotIncludeAccessTokenHash() {
            AuthSession session = createDefaultSession();

            assertThat(session.toString()).doesNotContain(ACCESS_HASH);
        }

        @Test
        @DisplayName("should not include refresh token hash in toString")
        void shouldNotIncludeRefreshTokenHash() {
            AuthSession session = createDefaultSession();

            assertThat(session.toString()).doesNotContain(REFRESH_HASH);
        }
    }
}
