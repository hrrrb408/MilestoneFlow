package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.port.out.PasswordResetEmailSender;
import com.milestoneflow.identity.infrastructure.config.PasswordResetProperties;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Locale;

/**
 * Production SMTP implementation of {@link PasswordResetEmailSender}.
 *
 * <p>Sends a plain-text password reset email containing a link with the raw token.
 * The raw token is used <strong>only</strong> to construct the link URL inside the
 * email body and is never logged, persisted, or returned in API responses.
 *
 * <p>Failures are caught by the {@code AFTER_COMMIT} listener and logged with
 * sanitized messages — they do not roll back the committed business transaction.
 *
 * <p>Only activated when:
 * <ul>
 *   <li>Active profile is {@code prod}</li>
 *   <li>{@code milestoneflow.mail.provider} is {@code smtp}</li>
 * </ul>
 */
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetEmailSender.class);

    private static final String SUBJECT = "Reset your MilestoneFlow password";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final PasswordResetProperties passwordResetProperties;

    public SmtpPasswordResetEmailSender(JavaMailSender mailSender,
                                        MailProperties mailProperties,
                                        PasswordResetProperties passwordResetProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.passwordResetProperties = passwordResetProperties;
    }

    @Override
    public void send(String recipientEmail, String displayName, String rawToken, Locale locale) {
        String resetLink = buildResetLink(rawToken);
        String body = buildBody(displayName, resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(mailProperties.from());
            message.setRecipients(Message.RecipientType.TO, recipientEmail);
            message.setSubject(SUBJECT);
            message.setText(body);

            mailSender.send(message);

            log.info("Password reset email sent to '{}', displayName='{}'",
                    maskEmail(recipientEmail), displayName);
        } catch (MessagingException e) {
            // Sanitized error — no raw token or URL in log
            log.error("Failed to build password reset email for '{}': {}",
                    maskEmail(recipientEmail), e.getClass().getSimpleName());
            throw new EmailSendException("Password reset email build failed", e);
        } catch (Exception e) {
            // Sanitized error — no raw token or URL in log
            log.error("Failed to send password reset email for '{}': {}",
                    maskEmail(recipientEmail), e.getClass().getSimpleName());
            throw new EmailSendException("Password reset email delivery failed", e);
        }
    }

    /**
     * Builds the password reset URL. The raw token appears only in the URL
     * inside the email body — never in logs, audit, or API responses.
     */
    private String buildResetLink(String rawToken) {
        return mailProperties.frontendBaseUrl()
                + mailProperties.passwordResetPath()
                + "?token=" + rawToken;
    }

    /**
     * Builds the plain-text email body.
     *
     * <p>Template includes: greeting, link, TTL, and ignore notice.
     * Password, token hash, cookie, requestId, and user status are never included.
     */
    private String buildBody(String displayName, String resetLink) {
        String ttlMinutes = String.valueOf(passwordResetProperties.tokenTtl().toMinutes());

        return """
                Hello %s,

                We received a request to reset your MilestoneFlow password.
                Click the link below to choose a new password:

                %s

                This link will expire in %s minutes.

                If you did not request a password reset, you can safely ignore this email.
                Your password will not be changed.

                — MilestoneFlow
                """.formatted(displayName, resetLink, ttlMinutes);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
