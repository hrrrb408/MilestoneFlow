package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for registering a new user.
 *
 * <p>The password field contains the raw plaintext password from the user.
 * toString() is overridden to prevent password leakage in logs.
 */
public final class RegisterUserCommand {

    private final String email;
    private final String displayName;
    private final String password;

    public RegisterUserCommand(String email, String displayName, String password) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the raw plaintext password.
     * Must not be logged or persisted.
     */
    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "RegisterUserCommand{email='" + email
                + "', displayName='" + displayName
                + "', password=[REDACTED]}";
    }
}
