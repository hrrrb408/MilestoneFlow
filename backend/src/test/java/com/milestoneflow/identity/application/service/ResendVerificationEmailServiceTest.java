package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ResendVerificationEmailCommand;
import com.milestoneflow.identity.application.event.EmailVerificationRequestedEvent;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ResendVerificationEmailService")
class ResendVerificationEmailServiceTest {

    private AppUserRepository userRepository;
    private VerificationTokenRepository tokenRepository;
    private SecureTokenGenerator tokenGenerator;
    private TokenHasher tokenHasher;
    private IdGenerator idGenerator;
    private Clock clock;
    private EmailVerificationProperties properties;
    private ApplicationEventPublisher eventPublisher;
    private ResendVerificationEmailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        tokenRepository = mock(VerificationTokenRepository.class);
        tokenGenerator = mock(SecureTokenGenerator.class);
        tokenHasher = mock(TokenHasher.class);
        idGenerator = mock(IdGenerator.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);
        properties = new EmailVerificationProperties(Duration.ofHours(24));
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new ResendVerificationEmailService(
                userRepository, tokenRepository, tokenGenerator,
                tokenHasher, idGenerator, clock, properties, eventPublisher
        );
    }

    @Nested
    @DisplayName("resend()")
    class Resend {

        @Test
        @DisplayName("unknown email returns without error (anti-enumeration)")
        void unknownEmail() {
            when(userRepository.findByEmailNormalized("unknown@example.com")).thenReturn(Optional.empty());

            assertThatCode(() -> service.resend(new ResendVerificationEmailCommand("unknown@example.com")))
                    .doesNotThrowAnyException();

            verify(tokenRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("ACTIVE user returns without error and does not send")
        void activeUser() {
            AppUser activeUser = createTestUser(UserStatus.ACTIVE);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(activeUser));

            assertThatCode(() -> service.resend(new ResendVerificationEmailCommand("user@example.com")))
                    .doesNotThrowAnyException();

            verify(tokenRepository, never()).deleteUnusedByUserIdAndPurpose(any(), any());
            verify(tokenRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("DISABLED user returns without error and does not send")
        void disabledUser() {
            AppUser disabledUser = createTestUser(UserStatus.DISABLED);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(disabledUser));

            assertThatCode(() -> service.resend(new ResendVerificationEmailCommand("user@example.com")))
                    .doesNotThrowAnyException();

            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("PENDING user deletes old unused EMAIL_VERIFICATION tokens")
        void deletesOldTokens() {
            AppUser pendingUser = createTestUser(UserStatus.PENDING_VERIFICATION);
            UUID userId = pendingUser.getId();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(pendingUser));
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID());
            when(tokenGenerator.generate()).thenReturn(new SecretToken("new-token"));
            when(tokenHasher.hash("new-token")).thenReturn("new-hash");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resend(new ResendVerificationEmailCommand("user@example.com"));

            verify(tokenRepository).deleteUnusedByUserIdAndPurpose(userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
        }

        @Test
        @DisplayName("only deletes EMAIL_VERIFICATION tokens, not PASSWORD_RESET")
        void onlyDeletesEmailVerification() {
            AppUser pendingUser = createTestUser(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(pendingUser));
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID());
            when(tokenGenerator.generate()).thenReturn(new SecretToken("t"));
            when(tokenHasher.hash(any())).thenReturn("h");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resend(new ResendVerificationEmailCommand("user@example.com"));

            verify(tokenRepository).deleteUnusedByUserIdAndPurpose(
                    pendingUser.getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            // Never called with PASSWORD_RESET
            verify(tokenRepository, never()).deleteUnusedByUserIdAndPurpose(
                    any(), eq(VerificationTokenPurpose.PASSWORD_RESET));
        }

        @Test
        @DisplayName("creates new VerificationToken")
        void createsNewToken() {
            AppUser pendingUser = createTestUser(UserStatus.PENDING_VERIFICATION);
            UUID newTokenId = UUID.randomUUID();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(pendingUser));
            when(idGenerator.nextId()).thenReturn(newTokenId);
            when(tokenGenerator.generate()).thenReturn(new SecretToken("new-raw-token"));
            when(tokenHasher.hash("new-raw-token")).thenReturn("new-hash-value");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resend(new ResendVerificationEmailCommand("user@example.com"));

            verify(tokenRepository).save(argThat(token ->
                    token.getUserId().equals(pendingUser.getId()) &&
                    token.getPurpose() == VerificationTokenPurpose.EMAIL_VERIFICATION
            ));
        }

        @Test
        @DisplayName("saves token hash not raw token")
        void savesTokenHash() {
            AppUser pendingUser = createTestUser(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(pendingUser));
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID());
            when(tokenGenerator.generate()).thenReturn(new SecretToken("secret-raw-token"));
            when(tokenHasher.hash("secret-raw-token")).thenReturn("hashed-token-value");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resend(new ResendVerificationEmailCommand("user@example.com"));

            verify(tokenRepository).save(argThat(token ->
                    !token.toString().contains("secret-raw-token")
            ));
        }

        @Test
        @DisplayName("publishes event for AFTER_COMMIT email delivery")
        void publishesEvent() {
            AppUser pendingUser = createTestUser(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(pendingUser));
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID());
            when(tokenGenerator.generate()).thenReturn(new SecretToken("raw"));
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resend(new ResendVerificationEmailCommand("user@example.com"));

            verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
        }

        @Test
        @DisplayName("public result does not expose user existence")
        void noExposureOfUserExistence() {
            // Unknown email: no exception
            when(userRepository.findByEmailNormalized("unknown@example.com")).thenReturn(Optional.empty());
            assertThatCode(() -> service.resend(new ResendVerificationEmailCommand("unknown@example.com")))
                    .doesNotThrowAnyException();

            // Known active email: no exception
            AppUser activeUser = createTestUser(UserStatus.ACTIVE);
            when(userRepository.findByEmailNormalized("active@example.com")).thenReturn(Optional.of(activeUser));
            assertThatCode(() -> service.resend(new ResendVerificationEmailCommand("active@example.com")))
                    .doesNotThrowAnyException();
        }
    }

    private AppUser createTestUser(UserStatus status) {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(id, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        if (status == UserStatus.ACTIVE) {
            user.activateAfterEmailVerification(Instant.now(clock));
        } else if (status == UserStatus.DISABLED) {
            user.disable();
        }
        return user;
    }
}
