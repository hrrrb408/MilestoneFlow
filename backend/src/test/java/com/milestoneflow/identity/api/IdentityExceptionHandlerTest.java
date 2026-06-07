package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.exception.RefreshTokenMissingException;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException;
import com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenReusedException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.shared.api.ApiErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link IdentityExceptionHandler}, focusing on the
 * safety-net handler for {@link DataIntegrityViolationException}.
 *
 * <p>Verifies that only {@code uk_app_user_email_normalized} constraint violations
 * are mapped to 409 AUTH_EMAIL_ALREADY_EXISTS, while all other data integrity
 * violations are re-thrown for the global 500 handler.
 */
@DisplayName("IdentityExceptionHandler")
class IdentityExceptionHandlerTest {

    private IdentityExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IdentityExceptionHandler(Clock.systemUTC());
    }

    // ── EmailAlreadyExistsException → 409 ──────────────────────────────

    @Nested
    @DisplayName("EmailAlreadyExistsException")
    class EmailAlreadyExists {

        @Test
        @DisplayName("maps to 409 CONFLICT")
        void mapsTo409() {
            ResponseEntity<ApiErrorResponse> response = handler.handleEmailAlreadyExists(
                    new EmailAlreadyExistsException(),
                    new MockHttpServletRequest("/auth/register")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("response code is AUTH_EMAIL_ALREADY_EXISTS")
        void responseCode() {
            ResponseEntity<ApiErrorResponse> response = handler.handleEmailAlreadyExists(
                    new EmailAlreadyExistsException(),
                    new MockHttpServletRequest("/auth/register")
            );

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("AUTH_EMAIL_ALREADY_EXISTS");
        }
    }

    // ── DataIntegrityViolationException safety-net ─────────────────────

    @Nested
    @DisplayName("DataIntegrityViolationException safety-net")
    class DataIntegritySafetyNet {

        @Test
        @DisplayName("email unique constraint → 409 AUTH_EMAIL_ALREADY_EXISTS")
        void emailUniqueConstraint_mapsTo409() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key", "23505"),
                    "uk_app_user_email_normalized"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                    dive, new MockHttpServletRequest("/auth/register"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("AUTH_EMAIL_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("other unique constraint → re-thrown (not 409)")
        void otherUniqueConstraint_rethrown() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key", "23505"),
                    "uk_verification_token_hash"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThatThrownBy(() ->
                    handler.handleDataIntegrityViolation(dive, new MockHttpServletRequest("/auth/register"))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("NOT NULL violation → re-thrown")
        void notNullViolation_rethrown() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("null value in column", "23502"),
                    null
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThatThrownBy(() ->
                    handler.handleDataIntegrityViolation(dive, new MockHttpServletRequest("/auth/register"))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("CHECK violation → re-thrown")
        void checkViolation_rethrown() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("check constraint", "23514"),
                    "ck_app_user_status"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThatThrownBy(() ->
                    handler.handleDataIntegrityViolation(dive, new MockHttpServletRequest("/auth/register"))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("FK violation → re-thrown")
        void fkViolation_rethrown() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("foreign key violation", "23503"),
                    "fk_verification_token_user"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThatThrownBy(() ->
                    handler.handleDataIntegrityViolation(dive, new MockHttpServletRequest("/auth/register"))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("no constraint name → re-thrown")
        void noConstraintName_rethrown() {
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "some error", new RuntimeException("root cause"));

            assertThatThrownBy(() ->
                    handler.handleDataIntegrityViolation(dive, new MockHttpServletRequest("/auth/register"))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Response security ──────────────────────────────────────────────

    @Nested
    @DisplayName("Response security")
    class ResponseSecurity {

        @Test
        @DisplayName("409 response does not contain constraint name")
        void noConstraintNameLeaked() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key", "23505"),
                    "uk_app_user_email_normalized"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                    dive, new MockHttpServletRequest("/auth/register"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).doesNotContain("uk_app_user_email_normalized");
        }

        @Test
        @DisplayName("409 response does not contain SQL")
        void noSqlLeaked() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key value violates unique constraint", "23505"),
                    "uk_app_user_email_normalized"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                    dive, new MockHttpServletRequest("/auth/register"));

            assertThat(response.getBody()).isNotNull();
            String body = response.getBody().toString();
            assertThat(body).doesNotContain("SELECT").doesNotContain("INSERT")
                    .doesNotContain("UPDATE").doesNotContain("DELETE");
        }

        @Test
        @DisplayName("409 response does not contain exception class name")
        void noExceptionClassNameLeaked() {
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key", "23505"),
                    "uk_app_user_email_normalized"
            );
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                    dive, new MockHttpServletRequest("/auth/register"));

            assertThat(response.getBody()).isNotNull();
            String body = response.getBody().toString();
            assertThat(body).doesNotContain("DataIntegrityViolationException")
                    .doesNotContain("ConstraintViolationException")
                    .doesNotContain("PSQLException");
        }

        @Test
        @DisplayName("requestId in response matches request URI")
        void requestIdConsistency() {
            ResponseEntity<ApiErrorResponse> response = handler.handleEmailAlreadyExists(
                    new EmailAlreadyExistsException(),
                    new MockHttpServletRequest("/auth/register")
            );

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().path()).isEqualTo("/auth/register");
        }
    }

    // ── Verification token exceptions ──────────────────────────────────

    @Nested
    @DisplayName("VerificationTokenInvalidException")
    class VerificationTokenInvalid {

        @Test
        @DisplayName("invalid type → 401 AUTH_VERIFICATION_TOKEN_INVALID")
        void invalidToken() {
            ResponseEntity<ApiErrorResponse> response = handler.handleVerificationTokenInvalid(
                    new VerificationTokenInvalidException("token_not_found"),
                    new MockHttpServletRequest("/auth/email-verification/confirm")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_VERIFICATION_TOKEN_INVALID");
        }

        @Test
        @DisplayName("expired type → 401 AUTH_VERIFICATION_TOKEN_EXPIRED")
        void expiredToken() {
            ResponseEntity<ApiErrorResponse> response = handler.handleVerificationTokenInvalid(
                    new VerificationTokenInvalidException(VerificationTokenInvalidException.Type.EXPIRED, "token_expired"),
                    new MockHttpServletRequest("/auth/email-verification/confirm")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_VERIFICATION_TOKEN_EXPIRED");
        }
    }

    // ── Account disabled ───────────────────────────────────────────────

    @Nested
    @DisplayName("AccountDisabledException")
    class AccountDisabled {

        @Test
        @DisplayName("maps to 401 AUTH_ACCOUNT_DISABLED")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleAccountDisabled(
                    new AccountDisabledException(),
                    new MockHttpServletRequest("/auth/email-verification/confirm")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_ACCOUNT_DISABLED");
        }
    }

    // ── Refresh token exceptions ────────────────────────────────────────

    @Nested
    @DisplayName("RefreshTokenMissingException")
    class RefreshTokenMissing {

        @Test
        @DisplayName("maps to 401 AUTH_UNAUTHENTICATED")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleRefreshTokenMissing(
                    new RefreshTokenMissingException(),
                    new MockHttpServletRequest("/auth/refresh")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_UNAUTHENTICATED");
        }
    }

    @Nested
    @DisplayName("RefreshTokenInvalidException")
    class RefreshTokenInvalid {

        @Test
        @DisplayName("maps to 401 AUTH_REFRESH_TOKEN_INVALID")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleRefreshTokenInvalid(
                    new RefreshTokenInvalidException(),
                    new MockHttpServletRequest("/auth/refresh")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
        }
    }

    @Nested
    @DisplayName("RefreshTokenExpiredException")
    class RefreshTokenExpired {

        @Test
        @DisplayName("maps to 401 AUTH_REFRESH_TOKEN_EXPIRED")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleRefreshTokenExpired(
                    new RefreshTokenExpiredException(),
                    new MockHttpServletRequest("/auth/refresh")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_REFRESH_TOKEN_EXPIRED");
        }
    }

    @Nested
    @DisplayName("RefreshTokenReusedException")
    class RefreshTokenReused {

        @Test
        @DisplayName("maps to 401 AUTH_REFRESH_TOKEN_REUSED")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleRefreshTokenReused(
                    new RefreshTokenReusedException(),
                    new MockHttpServletRequest("/auth/refresh")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_REFRESH_TOKEN_REUSED");
        }
    }

    @Nested
    @DisplayName("AuthSessionRevokedException")
    class AuthSessionRevoked {

        @Test
        @DisplayName("maps to 401 AUTH_SESSION_REVOKED")
        void mapsTo401() {
            ResponseEntity<ApiErrorResponse> response = handler.handleAuthSessionRevoked(
                    new AuthSessionRevokedException(),
                    new MockHttpServletRequest("/auth/refresh")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().code()).isEqualTo("AUTH_SESSION_REVOKED");
        }
    }

    // ── Mock HttpServletRequest ─────────────────────────────────────────

    /**
     * Minimal mock for {@link jakarta.servlet.http.HttpServletRequest}
     * that only provides getRequestURI().
     */
    static class MockHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final String requestURI;

        MockHttpServletRequest(String requestURI) {
            super(new org.springframework.mock.web.MockHttpServletRequest());
            this.requestURI = requestURI;
        }

        @Override
        public String getRequestURI() {
            return requestURI;
        }
    }
}
