package com.milestoneflow.identity.application.event;

import com.milestoneflow.identity.application.port.out.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link EmailVerificationRequestedEvent} after transaction commit
 * and sends the verification email.
 *
 * <p>Mail sending happens AFTER_COMMIT to ensure the database state is consistent
 * before the email is dispatched. Email failures do not roll back the transaction.
 */
@Component
public class EmailVerificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationEventListener.class);

    private final VerificationEmailSender emailSender;

    public EmailVerificationEventListener(VerificationEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(EmailVerificationRequestedEvent event) {
        try {
            emailSender.send(
                    event.getRecipientEmail(),
                    event.getDisplayName(),
                    event.getRawToken(),
                    event.getLocale()
            );
            log.info("Verification email dispatched for userId={}", event.getUserId());
        } catch (Exception e) {
            // Email failure must not roll back the committed transaction.
            // User can use the resend endpoint to get a new token.
            log.error("Failed to dispatch verification email for userId={}, errorCode={}",
                    event.getUserId(), e.getClass().getSimpleName());
        }
    }
}
