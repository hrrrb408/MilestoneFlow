package com.milestoneflow.identity.application.port.out;

import java.util.Locale;

/**
 * Output port for sending password reset emails.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Not log the raw reset token.</li>
 *   <li>Not persist the raw token.</li>
 *   <li>Handle failures gracefully without throwing back to the caller.</li>
 * </ul>
 *
 * <p><strong>Release Blocker:</strong> A real implementation is required
 * before production deployment.
 */
public interface PasswordResetEmailSender {

    /**
     * Sends a password reset email.
     *
     * @param recipientEmail the recipient's display email address
     * @param displayName    the user's display name
     * @param rawToken       the raw reset token (must not be logged)
     * @param locale         the user's preferred locale
     */
    void send(String recipientEmail, String displayName, String rawToken, Locale locale);
}
