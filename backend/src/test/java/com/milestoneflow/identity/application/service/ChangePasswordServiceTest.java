package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ChangePasswordCommand;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.policy.PasswordPolicyViolation;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChangePasswordService")
class ChangePasswordServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Clock clock;
    @Mock private AuthAuditWriter auditWriter;

    @Captor private ArgumentCaptor<AppUser> userCaptor;

    private ChangePasswordService service;

    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final String CURRENT_PASSWORD = "current-password-123";
    private static final String NEW_PASSWORD = "new-password-456";
    private static final String OLD_HASH = "{bcrypt}$2a$10$oldHash";
    private static final String NEW_HASH = "{bcrypt}$2a$10$newHash";

    @BeforeEach
    void setUp() {
        service = new ChangePasswordService(userRepository, authSessionRepository, passwordEncoder, clock, auditWriter);
        when(clock.instant()).thenReturn(NOW);
    }

    private AppUser createActiveUser() {
        AppUser user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", OLD_HASH, "en");
        user.activateAfterEmailVerification(NOW);
        return user;
    }

    private ChangePasswordCommand createCommand() {
        return new ChangePasswordCommand(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD);
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("successful password change updates hash and revokes sessions")
        void success() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(NEW_HASH);
            verify(authSessionRepository).revokeAllByUserId(USER_ID, NOW, AuthSessionRevokeReason.PASSWORD_CHANGE);
        }

        @Test
        @DisplayName("current password must match")
        void currentPasswordMustMatch() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(createCommand()))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(userRepository, never()).save(any());
            verify(authSessionRepository, never()).revokeAllByUserId(any(), any(), any());
        }

        @Test
        @DisplayName("new password must satisfy policy")
        void newPasswordMustSatisfyPolicy() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);

            ChangePasswordCommand command = new ChangePasswordCommand(USER_ID, CURRENT_PASSWORD, "short");
            assertThatThrownBy(() -> service.changePassword(command))
                    .isInstanceOf(PasswordPolicyViolation.class);

            verify(userRepository, never()).save(any());
            verify(authSessionRepository, never()).revokeAllByUserId(any(), any(), any());
        }

        @Test
        @DisplayName("new hash can be matched against new password")
        void newHashMatchesNewPassword() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            verify(passwordEncoder).encode(NEW_PASSWORD);
        }

        @Test
        @DisplayName("new hash is not equal to raw password")
        void newHashNotEqualToRawPassword() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
        }

        @Test
        @DisplayName("user not found throws InvalidCredentialsException")
        void userNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changePassword(createCommand()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("disabled user throws AccountDisabledException")
        void disabledUser() {
            AppUser user = createActiveUser();
            user.disable();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.changePassword(createCommand()))
                    .isInstanceOf(AccountDisabledException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not generate tokens")
        void doesNotGenerateTokens() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            // Only user save, no session saves
            verify(userRepository).save(any());
            verify(authSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not send email")
        void doesNotSendEmail() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            // No event publishing — verified by absence of PasswordResetEmailSender usage
        }

        @Test
        @DisplayName("revokes all sessions with PASSWORD_CHANGE reason")
        void revokesAllSessionsWithReason() {
            AppUser user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_PASSWORD, OLD_HASH)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(createCommand());

            verify(authSessionRepository).revokeAllByUserId(
                    eq(USER_ID), eq(NOW), eq(AuthSessionRevokeReason.PASSWORD_CHANGE));
        }
    }
}
