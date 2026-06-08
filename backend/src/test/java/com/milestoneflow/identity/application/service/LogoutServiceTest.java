package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService")
class LogoutServiceTest {

    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private Clock clock;

    @Captor private ArgumentCaptor<AuthSession> sessionCaptor;

    private LogoutService logoutService;

    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(authSessionRepository, clock);
        when(clock.instant()).thenReturn(NOW);
    }

    private AuthSession createActiveSession() {
        return AuthSession.create(SESSION_ID, USER_ID,
                "a".repeat(64), "b".repeat(64), FAMILY_ID,
                0, NOW.plus(Duration.ofMinutes(15)), NOW.plus(Duration.ofDays(30)),
                null, null);
    }

    // ── Logout ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("revokes active session with LOGOUT reason")
        void revokesActiveSession() {
            AuthSession session = createActiveSession();
            when(authSessionRepository.findByIdForUpdate(SESSION_ID))
                    .thenReturn(Optional.of(session));
            when(authSessionRepository.save(any(AuthSession.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            logoutService.logout(SESSION_ID);

            verify(authSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(sessionCaptor.getValue().getRevokeReason()).isEqualTo(AuthSessionRevokeReason.LOGOUT);
            assertThat(sessionCaptor.getValue().getRevokedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("throws AuthSessionRevokedException when session not found")
        void throwsWhenSessionNotFound() {
            when(authSessionRepository.findByIdForUpdate(SESSION_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> logoutService.logout(SESSION_ID))
                    .isInstanceOf(AuthSessionRevokedException.class);

            verify(authSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AuthSessionRevokedException when session already revoked")
        void throwsWhenAlreadyRevoked() {
            AuthSession session = createActiveSession();
            session.revoke(NOW, AuthSessionRevokeReason.REFRESH_ROTATED);
            when(authSessionRepository.findByIdForUpdate(SESSION_ID))
                    .thenReturn(Optional.of(session));

            assertThatThrownBy(() -> logoutService.logout(SESSION_ID))
                    .isInstanceOf(AuthSessionRevokedException.class);

            verify(authSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not generate new tokens")
        void doesNotGenerateTokens() {
            AuthSession session = createActiveSession();
            when(authSessionRepository.findByIdForUpdate(SESSION_ID))
                    .thenReturn(Optional.of(session));
            when(authSessionRepository.save(any(AuthSession.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            logoutService.logout(SESSION_ID);

            // Only one save — the revoke update. No new sessions created.
            verify(authSessionRepository).save(any());
        }
    }

    // ── Logout All ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logoutAll")
    class LogoutAll {

        @Test
        @DisplayName("revokes all active sessions via bulk update")
        void revokesAllSessions() {
            logoutService.logoutAll(USER_ID);

            verify(authSessionRepository).revokeAllByUserId(USER_ID, NOW, AuthSessionRevokeReason.LOGOUT_ALL);
        }

        @Test
        @DisplayName("succeeds idempotently when no active sessions exist")
        void idempotentWhenNoSessions() {
            // No exception expected — bulk update is idempotent
            logoutService.logoutAll(USER_ID);

            verify(authSessionRepository).revokeAllByUserId(USER_ID, NOW, AuthSessionRevokeReason.LOGOUT_ALL);
        }

        @Test
        @DisplayName("does not generate new tokens")
        void doesNotGenerateTokens() {
            logoutService.logoutAll(USER_ID);

            verify(authSessionRepository, never()).save(any());
        }
    }
}
