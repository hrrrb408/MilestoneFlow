package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.port.out.VerificationEmailSender;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Locale;

/**
 * Production SMTP implementation of {@link VerificationEmailSender}.
 *
 * <p>Sends a plain-text verification email containing a link with the raw token.
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
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationEmailSender.class);

    private static final String SUBJECT = "Verify your MilestoneFlow email";
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailVerificationProperties verificationProperties;

    public SmtpVerificationEmailSender(JavaMailSender mailSender,
                                       MailProperties mailProperties,
                                       EmailVerificationProperties verificationProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.verificationProperties = verificationProperties;
    }

    @Override
    public void send(String recipientEmail, String displayName, String rawToken, Locale locale) {
        String verificationLink = buildVerificationLink(rawToken);
        String body = buildBody(displayName, verificationLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(mailProperties.from());
            message.setRecipients(Message.RecipientType.TO, recipientEmail);
            message.setSubject(SUBJECT);
            message.setText(body);

            mailSender.send(message);

            log.info("Verification email sent to '{}', displayName='{}'",
                    maskEmail(recipientEmail), displayName);
        } catch (MessagingException e) {
            // Sanitized error — no raw token or URL in log
            log.error("Failed to build verification email for '{}': {}",
                    maskEmail(recipientEmail), e.getClass().getSimpleName());
            throw new EmailSendException("Verification email build failed", e);
        } catch (Exception e) {
            // Sanitized error — no raw token or URL in log
            log.error("Failed to send verification email for '{}': {}",
                    maskEmail(recipientEmail), e.getClass().getSimpleName());
            throw new EmailSendException("Verification email delivery failed", e);
        }
    }

    /**
     * Builds the verification URL. The raw token appears only in the URL
     * inside the email body — never in logs, audit, or API responses.
     */
    private String buildVerificationLink(String rawToken) {
        return mailProperties.frontendBaseUrl()
                + mailProperties.verificationPath()
                + "?token=" + rawToken;
    }

    /**
     * Builds the plain-text email body.
     *
     * <p>Template includes: greeting, link, TTL, and ignore notice.
     * Password, token hash, cookie, requestId, and user status are never included.
     */
    private String buildBody(String displayName, String verificationLink) {
        String ttlHours = String.valueOf(verificationProperties.tokenTtl().toHours());

        return """
                Hello %s,

                Please verify your MilestoneFlow email address by clicking the link below:

                %s

                This link will expire in %s hours.

                If you did not create a MilestoneFlow account, you can safely ignore this email.

                — MilestoneFlow
                """.formatted(displayName, verificationLink, ttlHours);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
