package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.ConfirmEmailVerificationCommand;
import com.milestoneflow.identity.application.result.EmailVerificationResult;

/**
 * Input port for confirming email verification.
 */
public interface ConfirmEmailVerificationUseCase {

    /**
     * Confirms email verification using the provided token.
     *
     * @param command contains the raw verification token
     * @return verification result with user ID, email, and status
     */
    EmailVerificationResult confirm(ConfirmEmailVerificationCommand command);
}
