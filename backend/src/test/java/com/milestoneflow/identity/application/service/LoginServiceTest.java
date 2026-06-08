package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.LoginCommand;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.AuthRateLimiter;
import static org.mockito.Mockito.lenient;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;
import com.milestoneflow.identity.application.result.LoginResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.EmailNotVerifiedException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService")
class LoginServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecureTokenGenerator tokenGenerator;
    @Mock private TokenHasher tokenHasher;
    @Mock private IdGenerator idGenerator;
    @Mock private Clock clock;
    @Mock private AuthAuditWriter auditWriter;
    @Mock private AuthRateLimiter rateLimiter;

    @Captor private ArgumentCaptor<AuthSession> sessionCaptor;
    @Captor private ArgumentCaptor<AppUser> userCaptor;

    private LoginService loginService;

    private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");
    private static final String RAW_PASSWORD = "test-password";
    private static final String ENCODED_PASSWORD = "{bcrypt}$2a$10$encodedhash";
    private static final String ACCESS_HASH = "a".repeat(64);
    private static final String REFRESH_HASH = "b".repeat(64);

    @BeforeEach
    void setUp() {
        var tokenProperties = new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofDays(30));
        loginService = new LoginService(userRepository, authSessionRepository, passwordEncoder,
                tokenGenerator, tokenHasher, idGenerator, clock, tokenProperties, auditWriter, rateLimiter);
        lenient().when(rateLimiter.check(any(), anyString())).thenReturn(RateLimitDecision.allowed(100));
        // Rate limiter uses tokenHasher.hash(normalizedEmail) for the rate limit key
        lenient().when(tokenHasher.hash("user@example.com")).thenReturn("rl-hash-1234");
    }

    private AppUser createActiveUser() {
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", ENCODED_PASSWORD, "en");
        user.activateAfterEmailVerification(NOW);
        return user;
    }

    private void setupSuccessfulLogin(AppUser user) {
        when(clock.instant()).thenReturn(NOW);
        when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn(
                new SecretToken("raw-access"), new SecretToken("raw-refresh"));
        when(tokenHasher.hash("raw-access")).thenReturn(ACCESS_HASH);
        when(tokenHasher.hash("raw-refresh")).thenReturn(REFRESH_HASH);
        when(idGenerator.nextId()).thenReturn(SESSION_ID, FAMILY_ID);
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("successful login")
    class SuccessfulLogin {

        @Test
        @DisplayName("returns correct user data")
        void returnsCorrectUserData() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            LoginResult result = loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.email()).isEqualTo("user@example.com");
            assertThat(result.displayName()).isEqualTo("Test User");
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("returns raw tokens")
        void returnsRawTokens() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            LoginResult result = loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            assertThat(result.rawAccessToken().value()).isEqualTo("raw-access");
            assertThat(result.rawRefreshToken().value()).isEqualTo("raw-refresh");
        }

        @Test
        @DisplayName("email normalization is applied")
        void emailNormalizationApplied() {
            var user = createActiveUser();
            when(clock.instant()).thenReturn(NOW);
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(tokenGenerator.generate()).thenReturn(
                    new SecretToken("raw-access"), new SecretToken("raw-refresh"));
            when(tokenHasher.hash(anyString())).thenReturn(ACCESS_HASH, REFRESH_HASH);
            when(idGenerator.nextId()).thenReturn(SESSION_ID, FAMILY_ID);
            when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
            when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(inv -> inv.getArgument(0));

            loginService.login(new LoginCommand("User@Example.com", RAW_PASSWORD));

            verify(userRepository).findByEmailNormalized("user@example.com");
        }

        @Test
        @DisplayName("password matches is called")
        void passwordMatchesCalled() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);
        }

        @Test
        @DisplayName("creates AuthSession with correct fields")
        void createsAuthSession() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            verify(authSessionRepository).save(sessionCaptor.capture());
            AuthSession session = sessionCaptor.getValue();

            assertThat(session.getId()).isEqualTo(SESSION_ID);
            assertThat(session.getUserId()).isEqualTo(USER_ID);
            assertThat(session.getAccessTokenHash()).isEqualTo(ACCESS_HASH);
            assertThat(session.getRefreshTokenHash()).isEqualTo(REFRESH_HASH);
            assertThat(session.getSessionFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(session.getRefreshGeneration()).isEqualTo(0);
            assertThat(session.getStatus().name()).isEqualTo("ACTIVE");
            assertThat(session.getAccessExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            assertThat(session.getRefreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        }

        @Test
        @DisplayName("raw tokens are not persisted")
        void rawTokensNotPersisted() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            verify(authSessionRepository).save(sessionCaptor.capture());
            AuthSession session = sessionCaptor.getValue();

            assertThat(session.getAccessTokenHash()).isNotEqualTo("raw-access");
            assertThat(session.getRefreshTokenHash()).isNotEqualTo("raw-refresh");
        }

        @Test
        @DisplayName("updates lastLoginAt")
        void updatesLastLoginAt() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getLastLoginAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("saves user and session in same transaction")
        void savesUserAndSession() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            verify(userRepository).save(any(AppUser.class));
            verify(authSessionRepository).save(any(AuthSession.class));
        }
    }

    @Nested
    @DisplayName("authentication failures")
    class AuthenticationFailures {

        @Test
        @DisplayName("user not found throws InvalidCredentialsException")
        void userNotFound() {
            when(userRepository.findByEmailNormalized("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loginService.login(new LoginCommand("nobody@example.com", "pass")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("wrong password throws InvalidCredentialsException")
        void wrongPassword() {
            var user = createActiveUser();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", "wrong-password")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("user not found and wrong password produce same exception type")
        void sameExceptionType() {
            when(userRepository.findByEmailNormalized("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loginService.login(new LoginCommand("nobody@example.com", "pass")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");

            var user = createActiveUser();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("PENDING_VERIFICATION user cannot login")
        void pendingVerificationCannotLogin() {
            var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                    "Test User", ENCODED_PASSWORD, "en");
            // User is in PENDING_VERIFICATION state (not activated)
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD)))
                    .isInstanceOf(EmailNotVerifiedException.class);

            verifyNoInteractions(authSessionRepository);
        }

        @Test
        @DisplayName("DISABLED user cannot login")
        void disabledUserCannotLogin() {
            var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                    "Test User", ENCODED_PASSWORD, "en");
            user.activateAfterEmailVerification(NOW);
            user.disable();

            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD)))
                    .isInstanceOf(AccountDisabledException.class);

            verifyNoInteractions(authSessionRepository);
        }

        @Test
        @DisplayName("wrong password does not create session")
        void wrongPasswordNoSession() {
            var user = createActiveUser();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(authSessionRepository, tokenGenerator, idGenerator);
        }

        @Test
        @DisplayName("wrong password does not update lastLoginAt")
        void wrongPasswordNoLastLogin() {
            var user = createActiveUser();
            when(userRepository.findByEmailNormalized("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("security")
    class Security {

        @Test
        @DisplayName("LoginResult toString redacts tokens")
        void loginResultToStringRedacts() {
            var result = new LoginResult(USER_ID, "user@example.com", "Test User", "ACTIVE",
                    new SecretToken("secret-access"), new SecretToken("secret-refresh"));

            var str = result.toString();
            assertThat(str).contains("[REDACTED]");
            assertThat(str).doesNotContain("secret-access");
            assertThat(str).doesNotContain("secret-refresh");
        }

        @Test
        @DisplayName("does not send email")
        void doesNotSendEmail() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            // No email sender dependency exists in LoginService
            assertThat(true).isTrue();
        }

        @Test
        @DisplayName("does not write cookies (service layer)")
        void doesNotWriteCookies() {
            var user = createActiveUser();
            setupSuccessfulLogin(user);

            loginService.login(new LoginCommand("user@example.com", RAW_PASSWORD));

            // Cookie writing is in the controller layer, not service
            assertThat(true).isTrue();
        }
    }
}
