package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.ResetPasswordCommand;

/**
 * Input port for confirming a password reset.
 *
 * <p>This endpoint is public (no authentication required). The reset token
 * must be valid, unused, and not expired. After a successful reset, all
 * active sessions for the user are revoked per B1 Baseline §9.
 */
public interface ResetPasswordUseCase {

    /**
     * Resets the user's password using a valid reset token.
     *
     * @param command contains the raw reset token and new password
     * @throws com.milestoneflow.identity.domain.exception.PasswordResetTokenInvalidException if token is invalid or already used
     * @throws com.milestoneflow.identity.domain.exception.PasswordResetTokenExpiredException if token has expired
     * @throws com.milestoneflow.identity.domain.exception.AccountDisabledException if user is disabled
     * @throws com.milestoneflow.identity.domain.policy.PasswordPolicyViolation if newPassword violates policy
     */
    void resetPassword(ResetPasswordCommand command);
}
