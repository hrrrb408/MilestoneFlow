package com.milestoneflow.identity.application.event;

import com.milestoneflow.identity.application.port.out.PasswordResetEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link PasswordResetRequestedEvent} after transaction commit
 * and sends the password reset email.
 *
 * <p>Mail sending happens AFTER_COMMIT to ensure the database state is consistent
 * before the email is dispatched. Email failures do not roll back the transaction.
 */
@Component
public class PasswordResetRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestedListener.class);

    private final PasswordResetEmailSender emailSender;

    public PasswordResetRequestedListener(PasswordResetEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PasswordResetRequestedEvent event) {
        try {
            emailSender.send(
                    event.getRecipientEmail(),
                    event.getDisplayName(),
                    event.getRawToken(),
                    event.getLocale()
            );
            log.info("Password reset email dispatched for userId={}", event.getUserId());
        } catch (Exception e) {
            // Email failure must not roll back the committed transaction.
            // User can request another reset token.
            log.error("Failed to dispatch password reset email for userId={}, errorCode={}",
                    event.getUserId(), e.getClass().getSimpleName());
        }
    }
}
