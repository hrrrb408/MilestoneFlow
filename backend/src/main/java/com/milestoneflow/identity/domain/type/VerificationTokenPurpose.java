package com.milestoneflow.identity.domain.type;

/**
 * Purpose of a {@link com.milestoneflow.identity.domain.model.VerificationToken}.
 *
 * <p>Enum names must match the database CHECK constraint exactly:
 * {@code purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')}.
 */
public enum VerificationTokenPurpose {

    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
