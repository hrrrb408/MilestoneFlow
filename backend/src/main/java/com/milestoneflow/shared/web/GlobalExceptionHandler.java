package com.milestoneflow.shared.web;

import com.milestoneflow.shared.api.ApiErrorDetail;
import com.milestoneflow.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Centralised exception handler that maps all exceptions to the unified
 * {@link ApiErrorResponse} format defined in architecture spec §10.
 *
 * <p>This handler covers framework-level exceptions only. Business-module-specific
 * exceptions are handled by their own module-local {@code @RestControllerAdvice}
 * classes to respect ARCH-001 (shared must not depend on business modules).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Handles {@code @Valid} body validation failures.
     * Returns 422 VALIDATION_FAILED with field-level details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorDetail(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? resolveFieldCode(fe) : "INVALID_VALUE",
                        fe.getDefaultMessage()
                ))
                .toList();

        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request field validation failed.",
                request.getRequestURI(),
                fieldErrors
        );
    }

    /**
     * Handles {@code @Validated} parameter / path constraint violations.
     * Returns 422 VALIDATION_FAILED.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> new ApiErrorDetail(
                        extractPropertyPath(cv),
                        "INVALID_VALUE",
                        cv.getMessage()
                ))
                .toList();

        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request parameter validation failed.",
                request.getRequestURI(),
                fieldErrors
        );
    }

    /**
     * Handles malformed or unreadable request body (invalid JSON, wrong content type).
     * Returns 400 INVALID_REQUEST.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request body is not readable. Check JSON syntax and content type.",
                request.getRequestURI()
        );
    }

    /**
     * Handles type conversion failures on path/query parameters.
     * Returns 400 INVALID_REQUEST.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message = String.format(
                "Parameter '%s' should be of type '%s'.",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                message,
                request.getRequestURI()
        );
    }

    /**
     * Fallback handler for all unhandled exceptions.
     * Returns 500 INTERNAL_ERROR without exposing internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllUnhandled(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
    }

    // ── Helpers ────────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            String path
    ) {
        return build(status, code, message, path, List.of());
    }

    protected ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            String path,
            List<ApiErrorDetail> fieldErrors
    ) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now(clock))
                .status(status.value())
                .code(code)
                .message(message)
                .requestId(resolveRequestId())
                .path(path)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    protected String resolveRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : "unknown";
    }

    private String resolveFieldCode(org.springframework.validation.FieldError fe) {
        // Use the validation annotation simple name as the code if a code is not set
        if (fe.getCode() != null) {
            return fe.getCode();
        }
        return "INVALID_VALUE";
    }

    private String extractPropertyPath(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        // Strip leading method name from parameter constraints (e.g. "methodName.arg0.field")
        int dot = path.indexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
