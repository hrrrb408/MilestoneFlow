package com.milestoneflow.identity.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for changing a user's password.
 *
 * <p>toString() is overridden to prevent password leakage in logs.
 */
public final class ChangePasswordCommand {

    private final UUID userId;
    private final String currentPassword;
    private final String newPassword;

    public ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.currentPassword = Objects.requireNonNull(currentPassword, "currentPassword must not be null");
        this.newPassword = Objects.requireNonNull(newPassword, "newPassword must not be null");
    }

    public UUID getUserId() {
        return userId;
    }

    /**
     * Returns the raw current password.
     * Must not be logged or persisted.
     */
    public String getCurrentPassword() {
        return currentPassword;
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
        return "ChangePasswordCommand{userId=" + userId
                + ", currentPassword=[REDACTED]"
                + ", newPassword=[REDACTED]}";
    }
}
