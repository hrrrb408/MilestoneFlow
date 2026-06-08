package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.exception.AuthRateLimitedException;
import com.milestoneflow.identity.application.exception.RefreshTokenMissingException;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.exception.EmailNotVerifiedException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenExpiredException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException;
import com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenReusedException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.identity.domain.policy.PasswordPolicyViolation;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Identity-module-specific exception handler.
 *
 * <p>Handles exceptions from the identity domain module and maps them to
 * the unified {@link ApiErrorResponse} format. Placed in the identity.api
 * layer to respect ARCH-001 (shared must not depend on business modules).
 *
 * <p>Delegates response building to the shared {@link GlobalExceptionHandler}
 * for consistent formatting.
 */
@RestControllerAdvice
public class IdentityExceptionHandler extends GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    public IdentityExceptionHandler(java.time.Clock clock) {
        super(clock);
    }

    /**
     * Handles duplicate email registration.
     * Returns 409 AUTH_EMAIL_ALREADY_EXISTS.
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                "AUTH_EMAIL_ALREADY_EXISTS",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles invalid, expired, or already-used verification tokens.
     *
     * <p>Per B1 Baseline §15:
     * <ul>
     *   <li>{@code AUTH_VERIFICATION_TOKEN_INVALID} (401) — token not found or already used</li>
     *   <li>{@code AUTH_VERIFICATION_TOKEN_EXPIRED} (401) — token has expired</li>
     * </ul>
     */
    @ExceptionHandler(VerificationTokenInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleVerificationTokenInvalid(
            VerificationTokenInvalidException ex,
            HttpServletRequest request
    ) {
        log.warn("Verification token rejected: reason={}", ex.getInternalReason());
        String code = ex.getType() == VerificationTokenInvalidException.Type.EXPIRED
                ? "AUTH_VERIFICATION_TOKEN_EXPIRED"
                : "AUTH_VERIFICATION_TOKEN_INVALID";
        return build(
                HttpStatus.UNAUTHORIZED,
                code,
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles login with wrong email or wrong password.
     * Returns 401 AUTH_INVALID_CREDENTIALS.
     *
     * <p>Same response for both cases to prevent account enumeration.
     * Per B1 Baseline §10 and §15.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles login attempt by a user whose email has not been verified.
     * Returns 403 AUTH_EMAIL_NOT_VERIFIED per B1 Baseline §15.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailNotVerified(
            EmailNotVerifiedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                "AUTH_EMAIL_NOT_VERIFIED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles attempts to verify email for a disabled account,
     * or login by a disabled user.
     * Returns 401 AUTH_ACCOUNT_DISABLED per B1 Baseline §15.
     */
    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(
            AccountDisabledException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_ACCOUNT_DISABLED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles password policy violations.
     * Returns 422 AUTH_PASSWORD_POLICY_VIOLATION.
     */
    @ExceptionHandler(PasswordPolicyViolation.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordPolicyViolation(
            PasswordPolicyViolation ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AUTH_PASSWORD_POLICY_VIOLATION",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles missing refresh token cookie.
     * Returns 401 AUTH_UNAUTHENTICATED.
     */
    @ExceptionHandler(RefreshTokenMissingException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenMissing(
            RefreshTokenMissingException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_UNAUTHENTICATED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles rate-limited authentication requests.
     * Returns 429 AUTH_RATE_LIMITED.
     *
     * <p>Does not expose the limit type, key, counter, or retry timing.
     */
    @ExceptionHandler(AuthRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimited(
            AuthRateLimitedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_RATE_LIMITED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles invalid (not found) refresh token.
     * Returns 401 AUTH_REFRESH_TOKEN_INVALID.
     */
    @ExceptionHandler(RefreshTokenInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenInvalid(
            RefreshTokenInvalidException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_TOKEN_INVALID",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles expired refresh token.
     * Returns 401 AUTH_REFRESH_TOKEN_EXPIRED.
     */
    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenExpired(
            RefreshTokenExpiredException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_TOKEN_EXPIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles replayed (already-rotated) refresh token.
     * Returns 401 AUTH_REFRESH_TOKEN_REUSED. Entire session family is revoked.
     */
    @ExceptionHandler(RefreshTokenReusedException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenReused(
            RefreshTokenReusedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_TOKEN_REUSED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles invalid or already-used password reset tokens.
     * Returns 401 AUTH_PASSWORD_RESET_TOKEN_INVALID per B1 Baseline §15.
     */
    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordResetTokenInvalid(
            PasswordResetTokenInvalidException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_PASSWORD_RESET_TOKEN_INVALID",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles expired password reset tokens.
     * Returns 401 AUTH_PASSWORD_RESET_TOKEN_EXPIRED per B1 Baseline §15.
     */
    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordResetTokenExpired(
            PasswordResetTokenExpiredException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_PASSWORD_RESET_TOKEN_EXPIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles revoked session (non-rotation reason).
     * Returns 401 AUTH_SESSION_REVOKED.
     */
    @ExceptionHandler(AuthSessionRevokedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthSessionRevoked(
            AuthSessionRevokedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTH_SESSION_REVOKED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Safety-net handler for database unique constraint violations that escape
     * the repository adapter (e.g., JPA deferred flush at commit time).
     *
     * <p>Walks the exception cause chain looking for Hibernate's
     * {@code ConstraintViolationException} and checks its constraint name.
     * Only {@code uk_app_user_email_normalized} is mapped to 409;
     * all other data integrity errors are re-thrown for the global 500 handler.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (isEmailUniqueConstraintViolation(ex)) {
            return build(
                    HttpStatus.CONFLICT,
                    "AUTH_EMAIL_ALREADY_EXISTS",
                    "Email is already registered",
                    request.getRequestURI()
            );
        }
        // Other data integrity violations are not ours to handle.
        // Re-throw to let the global fallback return 500.
        throw ex;
    }

    private static final String EMAIL_CONSTRAINT = "uk_app_user_email_normalized";

    /**
     * Walks the cause chain looking for Hibernate's ConstraintViolationException
     * and checks if the constraint name matches the email unique constraint.
     * Uses class-name matching to avoid a compile-time dependency on
     * Hibernate from the API layer (required by ArchUnit rules).
     */
    private static boolean isEmailUniqueConstraintViolation(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current.getClass().getName()
                    .equals("org.hibernate.exception.ConstraintViolationException")) {
                try {
                    var method = current.getClass().getMethod("getConstraintName");
                    String constraintName = (String) method.invoke(current);
                    if (EMAIL_CONSTRAINT.equals(constraintName)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // Reflection failed — not the expected Hibernate type
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
