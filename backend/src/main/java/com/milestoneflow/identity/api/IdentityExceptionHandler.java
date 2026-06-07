package com.milestoneflow.identity.api;

import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.identity.domain.policy.PasswordPolicyViolation;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * Returns 422 with a unified error to prevent token state enumeration.
     */
    @ExceptionHandler(VerificationTokenInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleVerificationTokenInvalid(
            VerificationTokenInvalidException ex,
            HttpServletRequest request
    ) {
        log.warn("Verification token rejected: reason={}", ex.getInternalReason());
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AUTH_VERIFICATION_TOKEN_INVALID_OR_EXPIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles attempts to verify email for a disabled account.
     * Returns 403 AUTH_ACCOUNT_DISABLED.
     */
    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(
            AccountDisabledException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
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
}
