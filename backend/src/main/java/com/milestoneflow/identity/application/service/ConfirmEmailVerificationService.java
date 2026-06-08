package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ConfirmEmailVerificationCommand;
import com.milestoneflow.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.result.EmailVerificationResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException.Type;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Application service for confirming email verification.
 *
 * <p>Confirmation flow:
 * <ol>
 *   <li>Hash the raw token</li>
 *   <li>Find token by hash + purpose with pessimistic lock</li>
 *   <li>Validate token (exists, unused, not expired)</li>
 *   <li>Load the associated user</li>
 *   <li>Validate user status</li>
 *   <li>Activate user and mark token used</li>
 *   <li>Save both in a single transaction</li>
 * </ol>
 *
 * <p>Concurrency: Uses pessimistic write lock on the token row to ensure
 * only one concurrent confirmation succeeds.
 */
@Service
public class ConfirmEmailVerificationService implements ConfirmEmailVerificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmEmailVerificationService.class);

    private final VerificationTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final AuthAuditWriter auditWriter;

    public ConfirmEmailVerificationService(VerificationTokenRepository tokenRepository,
                                           AppUserRepository userRepository,
                                           TokenHasher tokenHasher,
                                           Clock clock,
                                           AuthAuditWriter auditWriter) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public EmailVerificationResult confirm(ConfirmEmailVerificationCommand command) {
        // 1. Hash the raw token
        String tokenHash = tokenHasher.hash(command.getToken());

        // 2. Find token with pessimistic lock
        VerificationToken token = tokenRepository
                .findByTokenHashAndPurposeForUpdate(tokenHash, VerificationTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new VerificationTokenInvalidException("token_not_found"));

        Instant now = Instant.now(clock);

        // 3. Validate token
        if (token.getUsedAt() != null) {
            throw new VerificationTokenInvalidException("token_already_used");
        }
        if (token.isExpired(now)) {
            throw new VerificationTokenInvalidException(Type.EXPIRED, "token_expired");
        }

        // 4. Load associated user
        AppUser user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new VerificationTokenInvalidException("user_not_found"));

        // 5. Validate user status
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccountDisabledException();
        }

        // 6. Activate user and mark token used (both in same transaction)
        user.activateAfterEmailVerification(now);
        token.markUsed(now);

        userRepository.save(user);
        tokenRepository.save(token);

        log.info("Email verification completed userId={}", user.getId());

        auditWriter.writeUserEvent("AUTH_EMAIL_VERIFICATION_CONFIRMED", user.getId(), "app_user", user.getId(), MDC.get("requestId"), "Email verified", null);

        return new EmailVerificationResult(user.getId(), user.getEmail(), user.getStatus());
    }
}
