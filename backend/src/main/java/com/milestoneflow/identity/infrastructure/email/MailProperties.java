package com.milestoneflow.identity.infrastructure.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for email delivery.
 *
 * <p>Controls the email provider ({@code noop} for development, {@code smtp} for production),
 * sender identity, frontend URL paths for verification and password reset links, and
 * TTL display in email content.
 *
 * <p>Example configuration:
 * <pre>
 * milestoneflow:
 *   mail:
 *     provider: noop
 *     from: "no-reply@milestoneflow.local"
 *     frontend-base-url: "http://localhost:5173"
 *     verification-path: "/auth/verify-email"
 *     password-reset-path: "/auth/reset-password"
 *     sender-name: "MilestoneFlow"
 * </pre>
 *
 * <p>Production requires {@code provider=smtp} and HTTPS {@code frontend-base-url}.
 * This configuration does not contain secrets — SMTP credentials are configured
 * via standard {@code spring.mail.*} properties.
 */
@ConfigurationProperties(prefix = "milestoneflow.mail")
public record MailProperties(
        String provider,
        String from,
        String frontendBaseUrl,
        String verificationPath,
        String passwordResetPath,
        String senderName
) {

    private static final String DEFAULT_PROVIDER = "noop";
    private static final String DEFAULT_FROM = "no-reply@milestoneflow.local";
    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";
    private static final String DEFAULT_VERIFICATION_PATH = "/auth/verify-email";
    private static final String DEFAULT_PASSWORD_RESET_PATH = "/auth/reset-password";
    private static final String DEFAULT_SENDER_NAME = "MilestoneFlow";

    public MailProperties {
        if (provider == null || provider.isBlank()) {
            provider = DEFAULT_PROVIDER;
        }
        if (from == null || from.isBlank()) {
            from = DEFAULT_FROM;
        }
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            frontendBaseUrl = DEFAULT_FRONTEND_BASE_URL;
        }
        if (verificationPath == null || verificationPath.isBlank()) {
            verificationPath = DEFAULT_VERIFICATION_PATH;
        }
        if (passwordResetPath == null || passwordResetPath.isBlank()) {
            passwordResetPath = DEFAULT_PASSWORD_RESET_PATH;
        }
        if (senderName == null || senderName.isBlank()) {
            senderName = DEFAULT_SENDER_NAME;
        }
    }

    /**
     * Returns whether the provider is set to {@code noop}.
     */
    public boolean isNoop() {
        return "noop".equalsIgnoreCase(provider);
    }

    /**
     * Returns whether the provider is set to {@code smtp}.
     */
    public boolean isSmtp() {
        return "smtp".equalsIgnoreCase(provider);
    }

    /**
     * Returns the provider value, validated against allowed values.
     *
     * @throws IllegalArgumentException if provider is not {@code noop} or {@code smtp}
     */
    public String validatedProvider() {
        if (!isNoop() && !isSmtp()) {
            throw new IllegalArgumentException(
                    "milestoneflow.mail.provider must be 'noop' or 'smtp', got: " + provider);
        }
        return provider.toLowerCase();
    }
}
