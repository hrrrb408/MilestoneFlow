package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.RefreshTokenCommand;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.result.RefreshTokenResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException;
import com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenReusedException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import com.milestoneflow.identity.infrastructure.config.AuthTokenProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private AuthSessionFamilyRevocationService familyRevocationService;
    @Mock private SecureTokenGenerator tokenGenerator;
    @Mock private TokenHasher tokenHasher;
    @Mock private IdGenerator idGenerator;
    @Mock private Clock clock;

    @Captor private ArgumentCaptor<AuthSession> sessionCaptor;

    private RefreshTokenService refreshTokenService;

    private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID OLD_SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");
    private static final UUID NEW_SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abf");
    private static final String OLD_REFRESH_HASH = "b".repeat(64);
    private static final String NEW_ACCESS_HASH = "c".repeat(64);
    private static final String NEW_REFRESH_HASH = "d".repeat(64);
    private static final String ENCODED_PASSWORD = "{bcrypt}$2a$10$encodedhash";

    @BeforeEach
    void setUp() {
        var tokenProperties = new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofDays(30));
        refreshTokenService = new RefreshTokenService(
                authSessionRepository, appUserRepository, familyRevocationService,
                tokenGenerator, tokenHasher, idGenerator, clock, tokenProperties);
    }

    private AppUser createActiveUser() {
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", ENCODED_PASSWORD, "en");
        user.activateAfterEmailVerification(NOW);
        return user;
    }

    private AuthSession createActiveSession() {
        return AuthSession.create(OLD_SESSION_ID, USER_ID,
                "a".repeat(64), OLD_REFRESH_HASH, FAMILY_ID,
                0, NOW.plus(Duration.ofMinutes(15)), NOW.plus(Duration.ofDays(30)),
                null, null);
    }

    private void setupSuccessfulRefresh(AuthSession session, AppUser user) {
        when(clock.instant()).thenReturn(NOW);
        when(tokenHasher.hash("raw-refresh-token")).thenReturn(OLD_REFRESH_HASH);
        when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                .thenReturn(Optional.of(session));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(tokenGenerator.generate()).thenReturn(
                new SecretToken("new-raw-access"), new SecretToken("new-raw-refresh"));
        when(tokenHasher.hash("new-raw-access")).thenReturn(NEW_ACCESS_HASH);
        when(tokenHasher.hash("new-raw-refresh")).thenReturn(NEW_REFRESH_HASH);
        when(idGenerator.nextId()).thenReturn(NEW_SESSION_ID);
        when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Successful refresh ────────────────────────────────────────────────

    @Nested
    @DisplayName("successful refresh")
    class SuccessfulRefresh {

        @Test
        @DisplayName("returns new raw tokens")
        void returnsNewRawTokens() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            RefreshTokenResult result = refreshTokenService.refresh(
                    new RefreshTokenCommand("raw-refresh-token"));

            assertThat(result.rawAccessToken().value()).isEqualTo("new-raw-access");
            assertThat(result.rawRefreshToken().value()).isEqualTo("new-raw-refresh");
        }

        @Test
        @DisplayName("hashes the raw refresh token to find session")
        void hashesRawRefreshToken() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(tokenHasher).hash("raw-refresh-token");
            verify(authSessionRepository).findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH);
        }

        @Test
        @DisplayName("uses locked repository query")
        void usesLockedQuery() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository).findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH);
            verify(authSessionRepository, never()).findByRefreshTokenHash(anyString());
        }

        @Test
        @DisplayName("revokes old session as REFRESH_ROTATED")
        void revokesOldSession() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var savedOld = sessionCaptor.getAllValues().get(0);
            assertThat(savedOld.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(savedOld.getRevokeReason()).isEqualTo("REFRESH_ROTATED");
            assertThat(savedOld.getRevokedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("creates new session with same family")
        void createsNewSessionSameFamily() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getSessionFamilyId()).isEqualTo(FAMILY_ID);
        }

        @Test
        @DisplayName("new session has generation + 1")
        void newSessionGenerationIncremented() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getRefreshGeneration()).isEqualTo(1);
        }

        @Test
        @DisplayName("new session has new token hashes")
        void newSessionHasNewHashes() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getAccessTokenHash()).isEqualTo(NEW_ACCESS_HASH);
            assertThat(newSession.getRefreshTokenHash()).isEqualTo(NEW_REFRESH_HASH);
        }

        @Test
        @DisplayName("raw tokens are not persisted")
        void rawTokensNotPersisted() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getAccessTokenHash()).isNotEqualTo("new-raw-access");
            assertThat(newSession.getRefreshTokenHash()).isNotEqualTo("new-raw-refresh");
        }

        @Test
        @DisplayName("new session is ACTIVE")
        void newSessionIsActive() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("new session has correct user")
        void newSessionHasCorrectUser() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(authSessionRepository, times(2)).save(sessionCaptor.capture());
            var newSession = sessionCaptor.getAllValues().get(1);
            assertThat(newSession.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("generates two new tokens")
        void generatesTwoNewTokens() {
            var session = createActiveSession();
            var user = createActiveUser();
            setupSuccessfulRefresh(session, user);

            refreshTokenService.refresh(new RefreshTokenCommand("raw-refresh-token"));

            verify(tokenGenerator, times(2)).generate();
        }
    }

    // ── Token not found ──────────────────────────────────────────────────

    @Nested
    @DisplayName("token not found")
    class TokenNotFound {

        @Test
        @DisplayName("throws RefreshTokenInvalidException")
        void throwsInvalidException() {
            when(tokenHasher.hash("missing-token")).thenReturn("x".repeat(64));
            when(authSessionRepository.findByRefreshTokenHashForUpdate("x".repeat(64)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("missing-token")))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }

        @Test
        @DisplayName("does not generate new tokens")
        void doesNotGenerateTokens() {
            when(tokenHasher.hash("missing-token")).thenReturn("x".repeat(64));
            when(authSessionRepository.findByRefreshTokenHashForUpdate("x".repeat(64)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("missing-token")))
                    .isInstanceOf(RefreshTokenInvalidException.class);

            verifyNoInteractions(tokenGenerator);
        }
    }

    // ── Token expired ────────────────────────────────────────────────────

    @Nested
    @DisplayName("token expired")
    class TokenExpired {

        @Test
        @DisplayName("throws RefreshTokenExpiredException when refresh TTL passed")
        void throwsExpiredException() {
            var session = AuthSession.create(OLD_SESSION_ID, USER_ID,
                    "a".repeat(64), OLD_REFRESH_HASH, FAMILY_ID, 0,
                    NOW.minus(Duration.ofMinutes(15)), NOW.minus(Duration.ofDays(1)),
                    null, null);
            when(tokenHasher.hash("expired-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("expired-token")))
                    .isInstanceOf(RefreshTokenExpiredException.class);

            verifyNoInteractions(tokenGenerator);
        }

        @Test
        @DisplayName("throws RefreshTokenExpiredException for EXPIRED session status")
        void throwsExpiredExceptionForExpiredStatus() {
            var session = createActiveSession();
            session.markExpired(session.getRefreshExpiresAt());
            when(tokenHasher.hash("expired-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("expired-token")))
                    .isInstanceOf(RefreshTokenExpiredException.class);
        }
    }

    // ── Session revoked ──────────────────────────────────────────────────

    @Nested
    @DisplayName("session revoked (non-rotated)")
    class SessionRevoked {

        @Test
        @DisplayName("throws AuthSessionRevokedException for revoked session")
        void throwsRevokedException() {
            var session = createActiveSession();
            session.revoke(NOW, "USER_LOGOUT");
            when(tokenHasher.hash("revoked-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("revoked-token")))
                    .isInstanceOf(AuthSessionRevokedException.class);

            verifyNoInteractions(tokenGenerator);
        }

        @Test
        @DisplayName("does not trigger family revoke for non-rotated revocation")
        void doesNotTriggerFamilyRevoke() {
            var session = createActiveSession();
            session.revoke(NOW, "USER_LOGOUT");
            when(tokenHasher.hash("revoked-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("revoked-token")))
                    .isInstanceOf(AuthSessionRevokedException.class);

            verify(familyRevocationService, never()).revokeEntireFamily(any(), any());
        }
    }

    // ── Replay detection ─────────────────────────────────────────────────

    @Nested
    @DisplayName("replay detection")
    class ReplayDetection {

        @Test
        @DisplayName("throws RefreshTokenReusedException for rotated token reuse")
        void throwsReusedException() {
            var session = createActiveSession();
            session.revokeAsRotated(NOW);
            when(tokenHasher.hash("old-rotated-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("old-rotated-token")))
                    .isInstanceOf(RefreshTokenReusedException.class);
        }

        @Test
        @DisplayName("delegates family revocation to dedicated service")
        void delegatesFamilyRevocation() {
            var rotatedSession = createActiveSession();
            rotatedSession.revokeAsRotated(NOW);

            when(tokenHasher.hash("old-rotated-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(rotatedSession));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("old-rotated-token")))
                    .isInstanceOf(RefreshTokenReusedException.class);

            // Verify family revocation service was called (runs in REQUIRES_NEW TX)
            verify(familyRevocationService).revokeEntireFamily(FAMILY_ID, NOW);
        }

        @Test
        @DisplayName("does not generate new tokens on replay")
        void doesNotGenerateTokensOnReplay() {
            var session = createActiveSession();
            session.revokeAsRotated(NOW);
            when(tokenHasher.hash("old-rotated-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("old-rotated-token")))
                    .isInstanceOf(RefreshTokenReusedException.class);

            verifyNoInteractions(tokenGenerator, idGenerator);
        }

        @Test
        @DisplayName("replay response does not leak session state")
        void replayResponseDoesNotLeakState() {
            var session = createActiveSession();
            session.revokeAsRotated(NOW);
            when(tokenHasher.hash("old-rotated-token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("old-rotated-token")))
                    .isInstanceOf(RefreshTokenReusedException.class)
                    .hasMessage("Refresh token has been reused");
        }
    }

    // ── User validation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("user validation")
    class UserValidation {

        @Test
        @DisplayName("user not found throws RefreshTokenInvalidException")
        void userNotFound() {
            var session = createActiveSession();
            when(tokenHasher.hash("token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("token")))
                    .isInstanceOf(RefreshTokenInvalidException.class);

            verifyNoInteractions(tokenGenerator);
        }

        @Test
        @DisplayName("PENDING_VERIFICATION user throws RefreshTokenInvalidException")
        void pendingVerificationUser() {
            var session = createActiveSession();
            var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                    "Test User", ENCODED_PASSWORD, "en");
            when(tokenHasher.hash("token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("token")))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }

        @Test
        @DisplayName("DISABLED user throws AccountDisabledException")
        void disabledUser() {
            var session = createActiveSession();
            var user = createActiveUser();
            user.disable();
            when(tokenHasher.hash("token")).thenReturn(OLD_REFRESH_HASH);
            when(authSessionRepository.findByRefreshTokenHashForUpdate(OLD_REFRESH_HASH))
                    .thenReturn(Optional.of(session));
            when(clock.instant()).thenReturn(NOW);
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> refreshTokenService.refresh(
                    new RefreshTokenCommand("token")))
                    .isInstanceOf(AccountDisabledException.class);

            verifyNoInteractions(tokenGenerator);
        }
    }

    // ── Security ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("security")
    class Security {

        @Test
        @DisplayName("RefreshTokenResult toString redacts tokens")
        void resultToStringRedacts() {
            var result = new RefreshTokenResult(
                    new SecretToken("secret-access"), new SecretToken("secret-refresh"));

            var str = result.toString();
            assertThat(str).contains("[REDACTED]");
            assertThat(str).doesNotContain("secret-access");
            assertThat(str).doesNotContain("secret-refresh");
        }

        @Test
        @DisplayName("RefreshTokenCommand toString redacts token")
        void commandToStringRedacts() {
            var command = new RefreshTokenCommand("sensitive-token-value");

            var str = command.toString();
            assertThat(str).contains("[REDACTED]");
            assertThat(str).doesNotContain("sensitive-token-value");
        }
    }
}
