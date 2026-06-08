package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.LoginCommand;
import com.milestoneflow.identity.application.port.in.LoginUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.AuthRateLimiter;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.ratelimit.AuthRateLimitAction;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;
import com.milestoneflow.identity.application.exception.AuthRateLimitedException;
import com.milestoneflow.identity.application.result.LoginResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.EmailNotVerifiedException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.policy.EmailNormalizationResult;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.infrastructure.config.AuthTokenProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for user login.
 *
 * <p>Login flow:
 * <ol>
 *   <li>Normalize email</li>
 *   <li>Find user by normalized email</li>
 *   <li>Validate password (unified error for not found / wrong password)</li>
 *   <li>Check user status (PENDING_VERIFICATION → 403, DISABLED → 401)</li>
 *   <li>Generate access and refresh tokens</li>
 *   <li>Hash tokens and create AuthSession</li>
 *   <li>Update lastLoginAt</li>
 *   <li>Return result with raw tokens for cookie setting</li>
 * </ol>
 */
@Service
public class LoginService implements LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final AppUserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AuthTokenProperties tokenProperties;
    private final AuthAuditWriter auditWriter;
    private final AuthRateLimiter rateLimiter;

    public LoginService(AppUserRepository userRepository,
                        AuthSessionRepository authSessionRepository,
                        PasswordEncoder passwordEncoder,
                        SecureTokenGenerator tokenGenerator,
                        TokenHasher tokenHasher,
                        IdGenerator idGenerator,
                        Clock clock,
                        AuthTokenProperties tokenProperties,
                        AuthAuditWriter auditWriter,
                        AuthRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.tokenProperties = tokenProperties;
        this.auditWriter = auditWriter;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional
    public LoginResult login(LoginCommand command) {
        // 1. Normalize email
        EmailNormalizationResult emailResult = EmailNormalizationResult.normalize(command.getEmail());

        // 1b. Rate limit check
        String rateLimitKey = "login:" + tokenHasher.hash(emailResult.normalizedEmail());
        RateLimitDecision limitDecision = rateLimiter.check(AuthRateLimitAction.LOGIN, rateLimitKey);
        if (!limitDecision.allowed()) {
            auditWriter.writeSystemEvent("AUTH_RATE_LIMIT_REJECTED", "app_user", null,
                    MDC.get("requestId"), "Login rate limited",
                    java.util.Map.of("reasonCode", "login_rate_limited"));
            throw new AuthRateLimitedException();
        }

        // 2. Find user by normalized email
        Optional<AppUser> userOpt = userRepository.findByEmailNormalized(emailResult.normalizedEmail());

        // 3. User not found → same error as wrong password (anti-enumeration)
        if (userOpt.isEmpty()) {
            log.info("Login failed: user not found for email");
            auditWriter.writeSystemEvent("AUTH_LOGIN_FAILED", "app_user", null,
                    MDC.get("requestId"), "Login failed: user not found",
                    java.util.Map.of("reasonCode", "user_not_found"));
            throw new InvalidCredentialsException();
        }

        AppUser user = userOpt.get();

        // 4. Validate password
        if (!passwordEncoder.matches(command.getPassword(), user.getPasswordHash())) {
            log.info("Login failed: invalid credentials userId={}", user.getId());
            auditWriter.writeUserEvent("AUTH_LOGIN_FAILED", user.getId(),
                    "app_user", user.getId(), MDC.get("requestId"),
                    "Login failed: invalid credentials",
                    java.util.Map.of("reasonCode", "invalid_credentials"));
            throw new InvalidCredentialsException();
        }

        // 5. Check user status
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            log.info("Login failed: email not verified userId={}", user.getId());
            auditWriter.writeUserEvent("AUTH_LOGIN_FAILED", user.getId(),
                    "app_user", user.getId(), MDC.get("requestId"),
                    "Login failed: email not verified",
                    java.util.Map.of("reasonCode", "email_not_verified"));
            throw new EmailNotVerifiedException();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            log.info("Login failed: account disabled userId={}", user.getId());
            auditWriter.writeUserEvent("AUTH_LOGIN_FAILED", user.getId(),
                    "app_user", user.getId(), MDC.get("requestId"),
                    "Login failed: account disabled",
                    java.util.Map.of("reasonCode", "account_disabled"));
            throw new AccountDisabledException();
        }

        // 6. Generate tokens
        SecretToken rawAccessToken = tokenGenerator.generate();
        SecretToken rawRefreshToken = tokenGenerator.generate();

        // 7. Hash tokens
        String accessTokenHash = tokenHasher.hash(rawAccessToken.value());
        String refreshTokenHash = tokenHasher.hash(rawRefreshToken.value());

        // 8. Calculate expiration times
        Instant now = Instant.now(clock);
        Instant accessExpiresAt = now.plus(tokenProperties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(tokenProperties.refreshTokenTtl());

        // 9. Create AuthSession
        UUID sessionId = idGenerator.nextId();
        UUID sessionFamilyId = idGenerator.nextId();

        AuthSession session = AuthSession.create(
                sessionId,
                user.getId(),
                accessTokenHash,
                refreshTokenHash,
                sessionFamilyId,
                0, // refreshGeneration
                accessExpiresAt,
                refreshExpiresAt,
                null, // userAgent — not captured in this milestone
                null  // ipAddress — not captured in this milestone
        );

        // 10. Update lastLoginAt and save in same transaction
        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        authSessionRepository.save(session);

        log.info("Login succeeded userId={}", user.getId());

        rateLimiter.reset(AuthRateLimitAction.LOGIN, rateLimitKey);

        auditWriter.writeUserEvent("AUTH_LOGIN_SUCCEEDED", user.getId(),
                "auth_session", sessionId, MDC.get("requestId"),
                "User logged in", null);

        return new LoginResult(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name(),
                rawAccessToken,
                rawRefreshToken
        );
    }
}
