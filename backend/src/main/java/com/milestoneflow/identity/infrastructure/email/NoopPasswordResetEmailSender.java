package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.port.out.PasswordResetEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Development-only password reset email sender that logs a sanitized message.
 *
 * <p><strong>NOT suitable for production.</strong> This implementation does not
 * send real emails. It logs the recipient and user ID but never logs
 * the raw reset token.
 *
 * <p>Only activated when both conditions are met:
 * <ul>
 *   <li>Active Spring profile is {@code local} or {@code test}</li>
 *   <li>Configuration property {@code milestoneflow.mail.provider} is set to {@code noop}</li>
 * </ul>
 *
 * <p>In production, neither condition is met, so no {@link PasswordResetEmailSender}
 * bean exists. This causes the application context to fail at startup (fail-closed),
 * preventing password reset without a real email sender.
 */
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(name = "milestoneflow.mail.provider", havingValue = "noop")
public class NoopPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPasswordResetEmailSender.class);

    @Override
    public void send(String recipientEmail, String displayName, String rawToken, Locale locale) {
        // Intentionally does NOT log the raw token or full reset URL.
        log.info("[DEV-ONLY] Password reset email would be sent to '{}', displayName='{}'",
                maskEmail(recipientEmail), displayName);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
