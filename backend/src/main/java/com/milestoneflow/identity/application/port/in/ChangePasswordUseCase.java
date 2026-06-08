package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.ChangePasswordCommand;

/**
 * Input port for changing a user's password.
 *
 * <p>Requires authentication. The current password must be verified
 * before the new password is set. All active sessions are revoked
 * after a successful change per B1 Baseline §3.
 */
public interface ChangePasswordUseCase {

    /**
     * Changes the user's password.
     *
     * @param command contains userId, currentPassword, and newPassword
     * @throws com.milestoneflow.identity.domain.exception.InvalidCredentialsException if currentPassword is wrong
     * @throws com.milestoneflow.identity.domain.policy.PasswordPolicyViolation if newPassword violates policy
     * @throws com.milestoneflow.identity.domain.exception.AccountDisabledException if user is disabled
     */
    void changePassword(ChangePasswordCommand command);
}
