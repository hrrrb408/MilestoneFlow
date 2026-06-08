package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.ChangePasswordCommand;
import com.milestoneflow.identity.application.port.in.ChangePasswordUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.policy.PasswordPolicy;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Application service for changing a user's password.
 *
 * <p>Flow:
 * <ol>
 *   <li>Load user by ID</li>
 *   <li>Verify current password</li>
 *   <li>Validate new password against policy</li>
 *   <li>Encode and update password hash</li>
 *   <li>Revoke all active sessions (per B1 Baseline §3)</li>
 * </ol>
 *
 * <p>Cookie clearing is handled by the controller layer, not this service.
 */
@Service
public class ChangePasswordService implements ChangePasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

    private final AppUserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ChangePasswordService(AppUserRepository userRepository,
                                 AuthSessionRepository authSessionRepository,
                                 PasswordEncoder passwordEncoder,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        // 1. Load user
        AppUser user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> {
                    log.warn("Password change failed: user not found userId={}", command.getUserId());
                    return new InvalidCredentialsException();
                });

        // 2. Check user is active
        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("Password change failed: account disabled userId={}", user.getId());
            throw new AccountDisabledException();
        }

        // 3. Verify current password
        if (!passwordEncoder.matches(command.getCurrentPassword(), user.getPasswordHash())) {
            log.info("Password change failed: invalid credentials userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        // 4. Validate new password policy
        PasswordPolicy.validate(command.getNewPassword());

        // 5. Encode and update password hash
        String newEncodedPassword = passwordEncoder.encode(command.getNewPassword());
        user.changePasswordHash(newEncodedPassword);
        userRepository.save(user);

        // 6. Revoke all active sessions (per B1 Baseline §3)
        Instant now = Instant.now(clock);
        authSessionRepository.revokeAllByUserId(user.getId(), now, AuthSessionRevokeReason.PASSWORD_CHANGE);

        log.info("Password changed successfully: userId={}", user.getId());
    }
}
