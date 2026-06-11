package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ForgotPasswordCommand;
import com.milestoneflow.identity.application.event.PasswordResetRequestedEvent;
import com.milestoneflow.identity.application.port.in.ForgotPasswordUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.policy.EmailNormalizationResult;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.PasswordResetProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the forgot-password flow.
 *
 * <p>This service is designed to prevent account enumeration:
 * it always completes without error regardless of whether the email
 * exists or the user is eligible. Only ACTIVE users receive a reset token.
 *
 * <p>Flow for eligible users:
 * <ol>
 *   <li>Normalize email</li>
 *   <li>Find ACTIVE user</li>
 *   <li>Create new PASSWORD_RESET verification token</li>
 *   <li>Commit transaction</li>
 *   <li>Publish event (AFTER_COMMIT) to send reset email</li>
 * </ol>
 *
 * <p>Per B1 Baseline §9.2, multiple active reset tokens are allowed.
 * Old tokens remain valid until they expire or are used.
 */
@Service
public class ForgotPasswordService implements ForgotPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);

    private final AppUserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final PasswordResetProperties passwordResetProperties;
    private final ApplicationEventPublisher eventPublisher;

    public ForgotPasswordService(AppUserRepository userRepository,
                                 VerificationTokenRepository verificationTokenRepository,
                                 SecureTokenGenerator tokenGenerator,
                                 TokenHasher tokenHasher,
                                 IdGenerator idGenerator,
                                 Clock clock,
                                 PasswordResetProperties passwordResetProperties,
                                 ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.passwordResetProperties = passwordResetProperties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordCommand command) {
        // 1. Normalize email
        EmailNormalizationResult emailResult = EmailNormalizationResult.normalize(command.getEmail());

        // 2. Find user — no error if not found (anti-enumeration)
        Optional<AppUser> userOpt = userRepository.findByEmailNormalized(emailResult.normalizedEmail());
        if (userOpt.isEmpty()) {
            log.debug("Forgot password: email not found");
            return; // silent return — no token created, no email sent
        }

        AppUser user = userOpt.get();

        // 3. Only ACTIVE users are eligible
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.debug("Forgot password: user not eligible userId={} status={}", user.getId(), user.getStatus());
            return; // silent return — no token created, no email sent
        }

        // 4. Generate reset token
        SecretToken rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken.value());

        // 5. Calculate expiration
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(passwordResetProperties.tokenTtl());

        // 6. Create and save token
        UUID tokenId = idGenerator.nextId();
        VerificationToken token = VerificationToken.create(
                tokenId, user.getId(), VerificationTokenPurpose.PASSWORD_RESET,
                tokenHash, expiresAt);
        verificationTokenRepository.save(token);

        // 7. Publish event for AFTER_COMMIT email dispatch
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(
                user.getId(), user.getEmail(), user.getDisplayName(),
                rawToken.value(), Locale.forLanguageTag(user.getLocale()));
        eventPublisher.publishEvent(event);

        log.info("Password reset token created: userId={}", user.getId());
    }
}
