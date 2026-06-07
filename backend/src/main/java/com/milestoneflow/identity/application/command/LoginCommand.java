package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for user login.
 *
 * <p>The password field contains the raw plaintext password from the user.
 * toString() is overridden to prevent password leakage in logs.
 */
public final class LoginCommand {

    private final String email;
    private final String password;

    public LoginCommand(String email, String password) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
    }

    public String getEmail() {
        return email;
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
        return "LoginCommand{email='" + email + "', password=[REDACTED]}";
    }
}
