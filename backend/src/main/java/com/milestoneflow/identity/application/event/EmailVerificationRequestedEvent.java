package com.milestoneflow.identity.application.event;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Application event published after a user registration or resend request
 * is successfully committed to the database.
 *
 * <p>Contains the raw verification token for email delivery.
 * toString() is overridden to prevent token leakage.
 *
 * <p>Lifecycle: in-memory only, never persisted, never published to
 * external message queues. Consumed by an AFTER_COMMIT listener.
 */
public final class EmailVerificationRequestedEvent {

    private final UUID userId;
    private final String recipientEmail;
    private final String displayName;
    private final String rawToken;
    private final Locale locale;

    public EmailVerificationRequestedEvent(UUID userId, String recipientEmail,
                                           String displayName, String rawToken, Locale locale) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.recipientEmail = Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.rawToken = Objects.requireNonNull(rawToken, "rawToken must not be null");
        this.locale = locale != null ? locale : Locale.ENGLISH;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the raw verification token.
     * Must not be logged, persisted, or included in API responses.
     */
    public String getRawToken() {
        return rawToken;
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return "EmailVerificationRequestedEvent{userId=" + userId
                + ", recipientEmail='" + recipientEmail + "'"
                + ", displayName='" + displayName + "'"
                + ", rawToken=[REDACTED]"
                + ", locale=" + locale + '}';
    }
}
