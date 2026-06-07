package com.milestoneflow.identity.application.service;

import java.util.Objects;

/**
 * Secure wrapper for a raw token value.
 *
 * <p>This type prevents accidental token leakage through logging, toString(),
 * serialization, or API responses. The raw value is only accessible via
 * the explicit {@link #value()} method.
 *
 * <p>Lifecycle: in-memory only, never persisted, never serialized.
 */
public final class SecretToken {

    private final String rawValue;

    public SecretToken(String rawValue) {
        this.rawValue = Objects.requireNonNull(rawValue, "rawValue must not be null");
    }

    /**
     * Returns the raw token value.
     * Callers must not log, persist, or serialize this value.
     */
    public String value() {
        return rawValue;
    }

    /**
     * Always returns a redacted string to prevent token leakage.
     */
    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
