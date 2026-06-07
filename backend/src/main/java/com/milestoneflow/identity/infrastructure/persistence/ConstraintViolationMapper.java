package com.milestoneflow.identity.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

/**
 * Extracts PostgreSQL constraint names from {@link DataIntegrityViolationException}
 * cause chains using Hibernate's structured {@link ConstraintViolationException#getConstraintName()}.
 *
 * <p>This is more reliable than string-contains checks on exception messages,
 * which depend on message formatting that can change across Hibernate or
 * PostgreSQL JDBC driver versions.
 *
 * <p>Walks the cause chain with cycle protection to find the first
 * {@link ConstraintViolationException} that provides a constraint name.
 */
public final class ConstraintViolationMapper {

    /**
     * The unique constraint on {@code app_user.email_normalized}.
     */
    public static final String UK_APP_USER_EMAIL_NORMALIZED = "uk_app_user_email_normalized";

    /**
     * Maximum cause-chain depth to walk before giving up (cycle protection).
     */
    private static final int MAX_DEPTH = 20;

    private ConstraintViolationMapper() {
    }

    /**
     * Returns {@code true} if the exception was caused by a duplicate email
     * constraint violation ({@value #UK_APP_USER_EMAIL_NORMALIZED}).
     *
     * @param ex the DataIntegrityViolationException to inspect
     * @return true if this is a duplicate-email constraint violation
     */
    public static boolean isDuplicateEmail(DataIntegrityViolationException ex) {
        return extractConstraintName(ex)
                .filter(UK_APP_USER_EMAIL_NORMALIZED::equals)
                .isPresent();
    }

    /**
     * Extracts the constraint name from the exception cause chain.
     *
     * <p>Walks down through wrapped exceptions looking for a Hibernate
     * {@link ConstraintViolationException} whose {@code getConstraintName()}
     * returns a non-null value.
     *
     * @param ex the exception to inspect
     * @return the constraint name, or empty if not found
     */
    public static Optional<String> extractConstraintName(DataIntegrityViolationException ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < MAX_DEPTH) {
            if (current instanceof ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && !constraintName.isBlank()) {
                    return Optional.of(constraintName);
                }
            }
            current = current.getCause();
            depth++;
        }
        return Optional.empty();
    }
}
