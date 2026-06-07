package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.port.out.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Development-only email sender that logs a sanitized message.
 *
 * <p><strong>NOT suitable for production.</strong> This implementation does not
 * send real emails. It logs the recipient and user ID but never logs
 * the raw verification token.
 *
 * <p>Real email delivery adapter is deferred to a future task.
 */
@Component
public class NoopVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopVerificationEmailSender.class);

    @Override
    public void send(String recipientEmail, String displayName, String rawToken, Locale locale) {
        // Intentionally does NOT log the raw token or full verification URL.
        log.info("[DEV-ONLY] Verification email would be sent to '{}', displayName='{}'",
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
