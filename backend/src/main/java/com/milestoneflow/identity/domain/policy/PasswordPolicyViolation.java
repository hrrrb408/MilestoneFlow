package com.milestoneflow.identity.domain.policy;

/**
 * Thrown when a password does not meet the password policy requirements.
 *
 * <p>The exception message must not contain the actual password value.
 */
public class PasswordPolicyViolation extends RuntimeException {

    public PasswordPolicyViolation(String message) {
        super(message);
    }
}
