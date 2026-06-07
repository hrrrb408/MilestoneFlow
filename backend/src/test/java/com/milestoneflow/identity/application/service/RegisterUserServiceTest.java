package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.RegisterUserCommand;
import com.milestoneflow.identity.application.event.EmailVerificationRequestedEvent;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.result.RegistrationResult;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RegisterUserService")
class RegisterUserServiceTest {

    private AppUserRepository userRepository;
    private VerificationTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private SecureTokenGenerator tokenGenerator;
    private TokenHasher tokenHasher;
    private IdGenerator idGenerator;
    private Clock clock;
    private EmailVerificationProperties properties;
    private ApplicationEventPublisher eventPublisher;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        tokenRepository = mock(VerificationTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenGenerator = mock(SecureTokenGenerator.class);
        tokenHasher = mock(TokenHasher.class);
        idGenerator = mock(IdGenerator.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);
        properties = new EmailVerificationProperties(Duration.ofHours(24));
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new RegisterUserService(
                userRepository, tokenRepository, passwordEncoder,
                tokenGenerator, tokenHasher, idGenerator, clock,
                properties, eventPublisher
        );
    }

    @Nested
    @DisplayName("register()")
    class Register {

        private RegisterUserCommand command;

        @BeforeEach
        void initCommand() {
            command = new RegisterUserCommand("User@Example.COM", "Test User", "password123");
        }

        @Test
        @DisplayName("registers successfully and returns result")
        void registersSuccessfully() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            when(idGenerator.nextId()).thenReturn(userId, tokenId);
            when(userRepository.existsByEmailNormalized("user@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}$2a$10$encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("raw-token"));
            when(tokenHasher.hash("raw-token")).thenReturn("abc123hash");
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

            RegistrationResult result = service.register(command);

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("User@Example.COM");
            assertThat(result.status()).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("normalizes email: displayEmail preserves case, normalizedEmail is lowercase")
        void normalizesEmail() {
            UUID userId = UUID.randomUUID();
            when(idGenerator.nextId()).thenReturn(userId, UUID.randomUUID());
            when(userRepository.existsByEmailNormalized("user@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("token"));
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals("User@Example.COM") &&
                    user.getEmailNormalized().equals("user@example.com")
            ));
        }

        @Test
        @DisplayName("encodes password with PasswordEncoder")
        void encodesPassword() {
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}$2a$10$hash");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("t"));
            when(tokenHasher.hash(any())).thenReturn("h");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(argThat(user ->
                    user.getPasswordHash().equals("{bcrypt}$2a$10$hash")
            ));
        }

        @Test
        @DisplayName("creates user in PENDING_VERIFICATION status")
        void pendingVerificationStatus() {
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("t"));
            when(tokenHasher.hash(any())).thenReturn("h");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(userRepository).save(argThat(user ->
                    user.getStatus() == UserStatus.PENDING_VERIFICATION
            ));
        }

        @Test
        @DisplayName("uses UUID v7 for user ID")
        void usesUuidForUserId() {
            UUID expectedId = UUID.randomUUID();
            when(idGenerator.nextId()).thenReturn(expectedId, UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("t"));
            when(tokenHasher.hash(any())).thenReturn("h");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(userRepository).save(argThat(user -> user.getId().equals(expectedId)));
        }

        @Test
        @DisplayName("creates VerificationToken with EMAIL_VERIFICATION purpose")
        void createsEmailVerificationToken() {
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("raw-token"));
            when(tokenHasher.hash("raw-token")).thenReturn("token-hash-64-chars-long-abcdef0123456789abcdef0123456789abcd");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(tokenRepository).save(argThat(token ->
                    token.getPurpose() == VerificationTokenPurpose.EMAIL_VERIFICATION
            ));
        }

        @Test
        @DisplayName("saves token hash not raw token")
        void savesTokenHashNotRaw() {
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("raw-secret-token"));
            when(tokenHasher.hash("raw-secret-token")).thenReturn("hashed-value-64-chars-abcdef0123456789abcdef0123456789");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(tokenRepository).save(argThat(token ->
                    !token.toString().contains("raw-secret-token")
            ));
        }

        @Test
        @DisplayName("throws EmailAlreadyExistsException for duplicate email")
        void duplicateEmail() {
            when(userRepository.existsByEmailNormalized("user@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(EmailAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws EmailAlreadyExistsException when adapter converts constraint violation")
        void concurrentDuplicateEmail() {
            when(idGenerator.nextId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("t"));
            when(tokenHasher.hash(any())).thenReturn("h");

            // The adapter now converts DataIntegrityViolationException to EmailAlreadyExistsException
            when(userRepository.save(any())).thenThrow(new EmailAlreadyExistsException());

            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(EmailAlreadyExistsException.class);
        }

        @Test
        @DisplayName("throws PasswordPolicyViolation for short password")
        void shortPassword() {
            RegisterUserCommand shortPw = new RegisterUserCommand("a@b.com", "Name", "1234567");

            assertThatThrownBy(() -> service.register(shortPw))
                    .isInstanceOf(com.milestoneflow.identity.domain.policy.PasswordPolicyViolation.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("publishes event after successful registration")
        void publishesEvent() {
            UUID userId = UUID.randomUUID();
            when(idGenerator.nextId()).thenReturn(userId, UUID.randomUUID());
            when(userRepository.existsByEmailNormalized(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(tokenGenerator.generate()).thenReturn(new SecretToken("raw-token"));
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.register(command);

            verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
        }

        @Test
        @DisplayName("does not publish event on registration failure")
        void noEventOnFailure() {
            when(userRepository.existsByEmailNormalized(any())).thenReturn(true);

            try {
                service.register(command);
            } catch (EmailAlreadyExistsException ignored) {
            }

            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
