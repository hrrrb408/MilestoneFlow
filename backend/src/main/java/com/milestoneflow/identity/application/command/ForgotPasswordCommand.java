package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for requesting a password reset.
 *
 * <p>Contains the raw email from the user. Normalization is handled
 * by the application service.
 */
public final class ForgotPasswordCommand {

    private final String email;

    public ForgotPasswordCommand(String email) {
        this.email = Objects.requireNonNull(email, "email must not be null");
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "ForgotPasswordCommand{email='" + email + "'}";
    }
}
