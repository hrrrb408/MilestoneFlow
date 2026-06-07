package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ConfirmEmailVerificationCommand;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.result.EmailVerificationResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConfirmEmailVerificationService")
class ConfirmEmailVerificationServiceTest {

    private VerificationTokenRepository tokenRepository;
    private AppUserRepository userRepository;
    private TokenHasher tokenHasher;
    private Clock clock;
    private ConfirmEmailVerificationService service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(VerificationTokenRepository.class);
        userRepository = mock(AppUserRepository.class);
        tokenHasher = mock(TokenHasher.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), java.time.ZoneOffset.UTC);
        service = new ConfirmEmailVerificationService(tokenRepository, userRepository, tokenHasher, clock);
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("confirms valid token successfully")
        void confirmsValidToken() {
            UUID userId = UUID.randomUUID();
            AppUser user = createPendingUser(userId);
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate("token-hash", VerificationTokenPurpose.EMAIL_VERIFICATION))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EmailVerificationResult result = service.confirm(new ConfirmEmailVerificationCommand("raw-token"));

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("hashes raw token for lookup")
        void hashesRawToken() {
            when(tokenHasher.hash("my-raw-token")).thenReturn("hashed-lookup");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate("hashed-lookup", VerificationTokenPurpose.EMAIL_VERIFICATION))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("my-raw-token")))
                    .isInstanceOf(VerificationTokenInvalidException.class);

            verify(tokenHasher).hash("my-raw-token");
        }

        @Test
        @DisplayName("uses EMAIL_VERIFICATION purpose for lookup")
        void usesCorrectPurpose() {
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate("hash", VerificationTokenPurpose.EMAIL_VERIFICATION))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("token")))
                    .isInstanceOf(VerificationTokenInvalidException.class);

            verify(tokenRepository).findByTokenHashAndPurposeForUpdate("hash", VerificationTokenPurpose.EMAIL_VERIFICATION);
        }

        @Test
        @DisplayName("uses pessimistic lock query")
        void usesPessimisticLock() {
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.empty());

            try {
                service.confirm(new ConfirmEmailVerificationCommand("token"));
            } catch (VerificationTokenInvalidException ignored) {
            }

            verify(tokenRepository).findByTokenHashAndPurposeForUpdate(any(), any());
        }

        @Test
        @DisplayName("throws for non-existent token")
        void tokenNotFound() {
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("nonexistent")))
                    .isInstanceOf(VerificationTokenInvalidException.class);
        }

        @Test
        @DisplayName("throws for expired token")
        void expiredToken() {
            UUID userId = UUID.randomUUID();
            VerificationToken expiredToken = VerificationToken.create(
                    UUID.randomUUID(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    "hash", Instant.parse("2024-12-31T00:00:00Z") // expired before clock
            );

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("expired-token")))
                    .isInstanceOf(VerificationTokenInvalidException.class);
        }

        @Test
        @DisplayName("throws for already-used token")
        void usedToken() {
            UUID userId = UUID.randomUUID();
            VerificationToken usedToken = VerificationToken.create(
                    UUID.randomUUID(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    "hash", Instant.parse("2026-01-01T00:00:00Z")
            );
            usedToken.markUsed(Instant.now(clock));

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(usedToken));

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("used-token")))
                    .isInstanceOf(VerificationTokenInvalidException.class);
        }

        @Test
        @DisplayName("throws for user not found")
        void userNotFound() {
            UUID userId = UUID.randomUUID();
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("token")))
                    .isInstanceOf(VerificationTokenInvalidException.class);
        }

        @Test
        @DisplayName("throws AccountDisabledException for disabled user")
        void disabledUser() {
            UUID userId = UUID.randomUUID();
            AppUser disabledUser = createDisabledUser(userId);
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("token")))
                    .isInstanceOf(AccountDisabledException.class);
        }

        @Test
        @DisplayName("activates user: status becomes ACTIVE")
        void activatesUser() {
            UUID userId = UUID.randomUUID();
            AppUser user = createPendingUser(userId);
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.confirm(new ConfirmEmailVerificationCommand("raw-token"));

            verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
        }

        @Test
        @DisplayName("sets emailVerifiedAt")
        void setsEmailVerifiedAt() {
            UUID userId = UUID.randomUUID();
            AppUser user = createPendingUser(userId);
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.confirm(new ConfirmEmailVerificationCommand("raw-token"));

            verify(userRepository).save(argThat(u ->
                    u.getEmailVerifiedAt() != null &&
                    u.getEmailVerifiedAt().equals(Instant.now(clock))
            ));
        }

        @Test
        @DisplayName("marks token as used")
        void marksTokenUsed() {
            UUID userId = UUID.randomUUID();
            AppUser user = createPendingUser(userId);
            VerificationToken token = createValidToken(userId);

            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.confirm(new ConfirmEmailVerificationCommand("raw-token"));

            verify(tokenRepository).save(argThat(t -> t.getUsedAt() != null));
        }

        @Test
        @DisplayName("raw token does not appear in exception message")
        void rawTokenNotInException() {
            when(tokenHasher.hash(any())).thenReturn("hash");
            when(tokenRepository.findByTokenHashAndPurposeForUpdate(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand("my-secret-token")))
                    .isInstanceOf(VerificationTokenInvalidException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("my-secret-token"));
        }
    }

    private AppUser createPendingUser(UUID userId) {
        return AppUser.create(userId, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
    }

    private AppUser createDisabledUser(UUID userId) {
        AppUser user = createPendingUser(userId);
        user.disable();
        return user;
    }

    private VerificationToken createValidToken(UUID userId) {
        return VerificationToken.create(
                UUID.randomUUID(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                "token-hash", Instant.parse("2026-01-01T00:00:00Z") // future expiry
        );
    }
}
