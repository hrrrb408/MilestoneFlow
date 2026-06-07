package com.milestoneflow.identity.application.port.out;

import java.util.Locale;

/**
 * Output port for sending verification emails.
 *
 * <p>This port abstracts the email delivery mechanism. The current implementation
 * is a no-op for development; real email delivery is deferred to a future task.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Not log the raw verification token.</li>
 *   <li>Not persist the raw token.</li>
 *   <li>Handle failures gracefully without throwing back to the caller.</li>
 * </ul>
 */
public interface VerificationEmailSender {

    /**
     * Sends a verification email.
     *
     * @param recipientEmail the recipient's display email address
     * @param displayName    the user's display name
     * @param rawToken       the raw verification token (must not be logged)
     * @param locale         the user's preferred locale
     */
    void send(String recipientEmail, String displayName, String rawToken, Locale locale);
}
