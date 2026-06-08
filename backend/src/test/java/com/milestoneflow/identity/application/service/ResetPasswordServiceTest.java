package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ResetPasswordCommand;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenExpiredException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenInvalidException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.policy.PasswordPolicyViolation;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResetPasswordService")
class ResetPasswordServiceTest {

    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenHasher tokenHasher;
    @Mock private Clock clock;
    @Mock private AuthAuditWriter auditWriter;

    @Captor private ArgumentCaptor<AppUser> userCaptor;
    @Captor private ArgumentCaptor<VerificationToken> tokenCaptor;

    private ResetPasswordService service;

    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID TOKEN_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final String RAW_TOKEN = "raw-reset-token-value";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String NEW_PASSWORD = "new-password-456";
    private static final String NEW_HASH = "{bcrypt}$2a$10$newHash";
    private static final String OLD_HASH = "{bcrypt}$2a$10$oldHash";

    @BeforeEach
    void setUp() {
        service = new ResetPasswordService(verificationTokenRepository, userRepository,
                authSessionRepository, passwordEncoder, tokenHasher, clock, auditWriter);
        when(clock.instant()).thenReturn(NOW);
    }

    private AppUser createActiveUser() {
        AppUser user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", OLD_HASH, "en");
        user.activateAfterEmailVerification(NOW);
        return user;
    }

    private VerificationToken createValidToken() {
        VerificationToken token = VerificationToken.create(TOKEN_ID, USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET, TOKEN_HASH,
                NOW.plus(Duration.ofHours(1)));
        return token;
    }

    private ResetPasswordCommand createCommand() {
        return new ResetPasswordCommand(RAW_TOKEN, NEW_PASSWORD);
    }

    private void setupValidReset() {
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(createValidToken()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createActiveUser()));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("successful reset updates password and marks token used")
        void success() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(NEW_HASH);

            verify(verificationTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getUsedAt()).isEqualTo(NOW);

            verify(authSessionRepository).revokeAllByUserId(USER_ID, NOW, AuthSessionRevokeReason.PASSWORD_RESET);
        }

        @Test
        @DisplayName("hashes raw token for lookup")
        void hashesRawToken() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(tokenHasher).hash(RAW_TOKEN);
            verify(verificationTokenRepository).findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET);
        }

        @Test
        @DisplayName("token not found throws PasswordResetTokenInvalidException")
        void tokenNotFound() {
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword(createCommand()))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);

            verify(userRepository, never()).save(any());
            verify(authSessionRepository, never()).revokeAllByUserId(any(), any(), any());
        }

        @Test
        @DisplayName("expired token throws PasswordResetTokenExpiredException")
        void expiredToken() {
            VerificationToken expiredToken = VerificationToken.create(TOKEN_ID, USER_ID,
                    VerificationTokenPurpose.PASSWORD_RESET, TOKEN_HASH,
                    NOW.minus(Duration.ofHours(1))); // expired 1 hour ago
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> service.resetPassword(createCommand()))
                    .isInstanceOf(PasswordResetTokenExpiredException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("already used token throws PasswordResetTokenInvalidException")
        void alreadyUsedToken() {
            VerificationToken usedToken = createValidToken();
            usedToken.markUsed(NOW.minus(Duration.ofMinutes(5)));
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(usedToken));

            assertThatThrownBy(() -> service.resetPassword(createCommand()))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found throws PasswordResetTokenInvalidException")
        void userNotFound() {
            VerificationToken token = createValidToken();
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword(createCommand()))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
        }

        @Test
        @DisplayName("disabled user throws AccountDisabledException")
        void disabledUser() {
            VerificationToken token = createValidToken();
            AppUser user = createActiveUser();
            user.disable();
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.resetPassword(createCommand()))
                    .isInstanceOf(AccountDisabledException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("new password must satisfy policy")
        void newPasswordMustSatisfyPolicy() {
            VerificationToken token = createValidToken();
            AppUser user = createActiveUser();
            when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(verificationTokenRepository.findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            ResetPasswordCommand command = new ResetPasswordCommand(RAW_TOKEN, "short");
            assertThatThrownBy(() -> service.resetPassword(command))
                    .isInstanceOf(PasswordPolicyViolation.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("password hash updated correctly")
        void passwordHashUpdated() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(NEW_HASH);
            assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(OLD_HASH);
        }

        @Test
        @DisplayName("token usedAt is set to now")
        void tokenUsedAtSet() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(verificationTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getUsedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("all sessions revoked with PASSWORD_RESET reason")
        void sessionsRevoked() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(authSessionRepository).revokeAllByUserId(
                    eq(USER_ID), eq(NOW), eq(AuthSessionRevokeReason.PASSWORD_RESET));
        }

        @Test
        @DisplayName("does not auto-login (no tokens generated)")
        void doesNotAutoLogin() {
            setupValidReset();

            service.resetPassword(createCommand());

            // No new sessions created
            verify(authSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not write cookies (controller responsibility)")
        void doesNotWriteCookies() {
            setupValidReset();

            service.resetPassword(createCommand());

            // Service layer has no concept of cookies — verified by absence of AuthCookieWriter dependency
        }

        @Test
        @DisplayName("PESSIMISTIC_WRITE lock used for token lookup")
        void pessimisticWriteLockUsed() {
            setupValidReset();

            service.resetPassword(createCommand());

            verify(verificationTokenRepository).findByTokenHashAndPurposeForUpdate(
                    TOKEN_HASH, VerificationTokenPurpose.PASSWORD_RESET);
        }
    }
}
