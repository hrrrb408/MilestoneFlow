package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for resending a verification email.
 */
public final class ResendVerificationEmailCommand {

    private final String email;

    public ResendVerificationEmailCommand(String email) {
        this.email = Objects.requireNonNull(email, "email must not be null");
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "ResendVerificationEmailCommand{email='" + email + "'}";
    }
}
