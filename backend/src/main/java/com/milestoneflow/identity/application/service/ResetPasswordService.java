package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ResetPasswordCommand;
import com.milestoneflow.identity.application.port.in.ResetPasswordUseCase;
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
import com.milestoneflow.identity.domain.policy.PasswordPolicy;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application service for confirming a password reset.
 *
 * <p>Flow:
 * <ol>
 *   <li>Hash raw token</li>
 *   <li>Find PASSWORD_RESET token with PESSIMISTIC_WRITE lock</li>
 *   <li>Validate token: exists, not used, not expired</li>
 *   <li>Load user and verify ACTIVE status</li>
 *   <li>Validate new password against policy</li>
 *   <li>Encode and update password hash</li>
 *   <li>Mark token as used</li>
 *   <li>Revoke all active sessions</li>
 *   <li>Commit transaction</li>
 * </ol>
 *
 * <p>The PESSIMISTIC_WRITE lock ensures that concurrent reset attempts
 * using the same token are serialized — only one succeeds.
 */
@Service
public class ResetPasswordService implements ResetPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordService.class);

    private final VerificationTokenRepository verificationTokenRepository;
    private final AppUserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final AuthAuditWriter auditWriter;

    public ResetPasswordService(VerificationTokenRepository verificationTokenRepository,
                                AppUserRepository userRepository,
                                AuthSessionRepository authSessionRepository,
                                PasswordEncoder passwordEncoder,
                                TokenHasher tokenHasher,
                                Clock clock,
                                AuthAuditWriter auditWriter) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        Instant now = Instant.now(clock);

        // 1. Hash the raw token
        String tokenHash = tokenHasher.hash(command.getRawToken());

        // 2. Find token with PESSIMISTIC_WRITE lock
        Optional<VerificationToken> tokenOpt = verificationTokenRepository
                .findByTokenHashAndPurposeForUpdate(tokenHash, VerificationTokenPurpose.PASSWORD_RESET);

        if (tokenOpt.isEmpty()) {
            log.warn("Password reset failed: token not found");
            throw new PasswordResetTokenInvalidException();
        }

        VerificationToken token = tokenOpt.get();

        // 3. Validate token — not already used
        if (token.getUsedAt() != null) {
            log.warn("Password reset failed: token already used");
            throw new PasswordResetTokenInvalidException();
        }

        // 4. Validate token — not expired
        if (token.isExpired(now)) {
            log.warn("Password reset failed: token expired");
            throw new PasswordResetTokenExpiredException();
        }

        // 5. Load user
        Optional<AppUser> userOpt = userRepository.findById(token.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("Password reset failed: user not found");
            throw new PasswordResetTokenInvalidException();
        }

        AppUser user = userOpt.get();

        // 6. Verify user is ACTIVE
        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("Password reset failed: account disabled userId={}", user.getId());
            throw new AccountDisabledException();
        }

        // 7. Validate new password policy
        PasswordPolicy.validate(command.getNewPassword());

        // 8. Encode and update password hash
        String encodedPassword = passwordEncoder.encode(command.getNewPassword());
        user.changePasswordHash(encodedPassword);
        userRepository.save(user);

        // 9. Mark token as used
        token.markUsed(now);
        verificationTokenRepository.save(token);

        // 10. Revoke all active sessions (per B1 Baseline §9)
        authSessionRepository.revokeAllByUserId(user.getId(), now, AuthSessionRevokeReason.PASSWORD_RESET);

        log.info("Password reset succeeded: userId={}", user.getId());

        auditWriter.writeUserEvent("AUTH_PASSWORD_RESET_SUCCEEDED", user.getId(), "app_user", user.getId(), MDC.get("requestId"), "Password reset completed", null);
    }
}
