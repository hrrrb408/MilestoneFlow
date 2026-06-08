package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.ForgotPasswordCommand;

/**
 * Input port for requesting a password reset.
 *
 * <p>This endpoint is public (no authentication required) and always
 * returns success to prevent account enumeration per B1 Baseline §10.
 * A reset token is only created if the email belongs to an ACTIVE user.
 */
public interface ForgotPasswordUseCase {

    /**
     * Requests a password reset for the given email.
     *
     * <p>If the email belongs to an ACTIVE user, a reset token is created
     * and a reset email is dispatched after transaction commit.
     * If the email is unknown or the user is not ACTIVE, no token is created
     * and no email is sent, but the caller cannot distinguish this case.
     *
     * @param command contains the user's email
     */
    void forgotPassword(ForgotPasswordCommand command);
}
