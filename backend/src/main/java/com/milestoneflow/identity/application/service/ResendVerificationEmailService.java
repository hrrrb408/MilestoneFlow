package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ResendVerificationEmailCommand;
import com.milestoneflow.identity.application.event.EmailVerificationRequestedEvent;
import com.milestoneflow.identity.application.port.in.ResendVerificationEmailUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.AuthRateLimiter;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.ratelimit.AuthRateLimitAction;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.policy.EmailNormalizationResult;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Application service for resending verification emails.
 *
 * <p>Anti-enumeration: always returns successfully regardless of whether the
 * email exists, the account is ACTIVE, DISABLED, or PENDING_VERIFICATION.
 * Only PENDING_VERIFICATION users actually receive a new token.
 *
 * <p>Per B1 Baseline §8.2: "Previous token remains valid until expiry or used.
 * Multiple valid tokens allowed." Old tokens are NOT deleted on resend.
 *
 * <p>Rate limit hook deferred to MF-BE-011.
 */
@Service
public class ResendVerificationEmailService implements ResendVerificationEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendVerificationEmailService.class);

    private final AppUserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthAuditWriter auditWriter;
    private final AuthRateLimiter rateLimiter;

    public ResendVerificationEmailService(AppUserRepository userRepository,
                                          VerificationTokenRepository tokenRepository,
                                          SecureTokenGenerator tokenGenerator,
                                          TokenHasher tokenHasher,
                                          IdGenerator idGenerator,
                                          Clock clock,
                                          EmailVerificationProperties properties,
                                          ApplicationEventPublisher eventPublisher,
                                          AuthAuditWriter auditWriter,
                                          AuthRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.auditWriter = auditWriter;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional
    public void resend(ResendVerificationEmailCommand command) {
        // Normalize the email for lookup
        EmailNormalizationResult emailResult = EmailNormalizationResult.normalize(command.getEmail());

        // Rate limit check
        String rateLimitKey = "resend:" + tokenHasher.hash(emailResult.normalizedEmail());
        RateLimitDecision limitDecision = rateLimiter.check(AuthRateLimitAction.EMAIL_VERIFICATION_RESEND, rateLimitKey);
        if (!limitDecision.allowed()) {
            // Per anti-enumeration: don't expose whether email exists
            // Just return silently as if the request succeeded
            return;
        }

        // Find user by normalized email
        var userOpt = userRepository.findByEmailNormalized(emailResult.normalizedEmail());

        if (userOpt.isEmpty()) {
            // Unknown email — return silently (anti-enumeration)
            log.debug("Resend requested for unknown email");
            auditWriter.writeSystemEvent("AUTH_EMAIL_VERIFICATION_RESEND_REQUESTED", "app_user", null, MDC.get("requestId"), "Resend requested for unknown email", null);
            return;
        }

        AppUser user = userOpt.get();

        // Only process PENDING_VERIFICATION users
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            // ACTIVE or DISABLED — return silently (anti-enumeration)
            log.debug("Resend skipped for user userId={} status={}", user.getId(), user.getStatus());
            auditWriter.writeUserEvent("AUTH_EMAIL_VERIFICATION_RESEND_REQUESTED", user.getId(), "app_user", user.getId(), MDC.get("requestId"), "Resend skipped: user not eligible", null);
            return;
        }

        // Per B1 Baseline §8.2: "Previous token remains valid until expiry or used.
        // Multiple valid tokens allowed." — do NOT delete old tokens.
        // Generate new token alongside existing ones.
        SecretToken secretToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(secretToken.value());

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.tokenTtl());

        VerificationToken newToken = VerificationToken.create(
                idGenerator.nextId(),
                user.getId(),
                VerificationTokenPurpose.EMAIL_VERIFICATION,
                tokenHash,
                expiresAt
        );

        tokenRepository.save(newToken);

        auditWriter.writeUserEvent("AUTH_EMAIL_VERIFICATION_RESEND_REQUESTED", user.getId(), "app_user", user.getId(), MDC.get("requestId"), "Verification email resent", null);

        // Publish event for AFTER_COMMIT email delivery
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                secretToken.value(),
                Locale.forLanguageTag(user.getLocale())
        ));
    }
}
