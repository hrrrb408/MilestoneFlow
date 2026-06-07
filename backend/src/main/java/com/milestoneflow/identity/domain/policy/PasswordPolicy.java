package com.milestoneflow.identity.domain.policy;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Password policy for user registration.
 *
 * <p>Per B1 Authentication Baseline:
 * <ul>
 *   <li>Minimum 8 Unicode code points.</li>
 *   <li>No mandatory uppercase, lowercase, digit, or special character.</li>
 *   <li>Passwords are not trimmed (leading/trailing whitespace is part of the password).</li>
 *   <li>No Unicode normalization applied to passwords.</li>
 *   <li>BCrypt 72-byte limit: passwords exceeding 72 bytes in UTF-8 are rejected.</li>
 * </ul>
 */
public final class PasswordPolicy {

    /**
     * BCrypt processes only the first 72 bytes of a password.
     * Passwords longer than this are silently truncated, which is a security risk.
     */
    public static final int MAX_UTF8_BYTES = 72;

    /**
     * Minimum password length in Unicode code points.
     */
    public static final int MIN_CODE_POINTS = 8;

    private PasswordPolicy() {
        // Utility class
    }

    /**
     * Validates the given raw password against the policy.
     *
     * @param rawPassword the raw password from the user
     * @throws NullPointerException     if rawPassword is null
     * @throws PasswordPolicyViolation if the password does not meet requirements
     */
    public static void validate(String rawPassword) {
        Objects.requireNonNull(rawPassword, "password must not be null");

        if (rawPassword.isEmpty()) {
            throw new PasswordPolicyViolation("Password must not be empty");
        }

        long codePoints = rawPassword.codePoints().count();
        if (codePoints < MIN_CODE_POINTS) {
            throw new PasswordPolicyViolation(
                    "Password must be at least " + MIN_CODE_POINTS + " characters");
        }

        int utf8Length = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Length > MAX_UTF8_BYTES) {
            throw new PasswordPolicyViolation(
                    "Password is too long (exceeds " + MAX_UTF8_BYTES + " bytes in UTF-8)");
        }
    }
}
