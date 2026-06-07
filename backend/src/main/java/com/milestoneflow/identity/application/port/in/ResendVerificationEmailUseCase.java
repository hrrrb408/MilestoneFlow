package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.ResendVerificationEmailCommand;

/**
 * Input port for resending verification emails.
 *
 * <p>Always returns the same public response regardless of whether
 * the email exists or the account status, to prevent account enumeration.
 */
public interface ResendVerificationEmailUseCase {

    /**
     * Resends a verification email if the account is eligible.
     *
     * @param command contains the email to resend to
     */
    void resend(ResendVerificationEmailCommand command);
}
