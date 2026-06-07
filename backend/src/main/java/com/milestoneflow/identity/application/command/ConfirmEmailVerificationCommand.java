package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for confirming email verification.
 *
 * <p>The token field contains the raw verification token from the user.
 * toString() is overridden to prevent token leakage in logs.
 */
public final class ConfirmEmailVerificationCommand {

    private final String token;

    public ConfirmEmailVerificationCommand(String token) {
        this.token = Objects.requireNonNull(token, "token must not be null");
    }

    /**
     * Returns the raw verification token.
     * Must not be logged or persisted.
     */
    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "ConfirmEmailVerificationCommand{token=[REDACTED]}";
    }
}
