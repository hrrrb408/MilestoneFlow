package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for resetting a password with a valid reset token.
 *
 * <p>toString() is overridden to prevent token and password leakage in logs.
 */
public final class ResetPasswordCommand {

    private final String rawToken;
    private final String newPassword;

    public ResetPasswordCommand(String rawToken, String newPassword) {
        this.rawToken = Objects.requireNonNull(rawToken, "rawToken must not be null");
        this.newPassword = Objects.requireNonNull(newPassword, "newPassword must not be null");
    }

    /**
     * Returns the raw reset token.
     * Must not be logged or persisted.
     */
    public String getRawToken() {
        return rawToken;
    }

    /**
     * Returns the raw new password.
     * Must not be logged or persisted.
     */
    public String getNewPassword() {
        return newPassword;
    }

    @Override
    public String toString() {
        return "ResetPasswordCommand{rawToken=[REDACTED], newPassword=[REDACTED]}";
    }
}
