package com.milestoneflow.identity.api;

import com.milestoneflow.identity.api.request.ChangePasswordRequest;
import com.milestoneflow.identity.api.request.ForgotPasswordRequest;
import com.milestoneflow.identity.api.request.ResetPasswordRequest;
import com.milestoneflow.identity.api.response.ForgotPasswordResponse;
import com.milestoneflow.identity.api.response.PasswordChangeResponse;
import com.milestoneflow.identity.api.response.ResetPasswordResponse;
import com.milestoneflow.identity.application.command.ChangePasswordCommand;
import com.milestoneflow.identity.application.command.ForgotPasswordCommand;
import com.milestoneflow.identity.application.command.ResetPasswordCommand;
import com.milestoneflow.identity.application.port.in.ChangePasswordUseCase;
import com.milestoneflow.identity.application.port.in.ForgotPasswordUseCase;
import com.milestoneflow.identity.application.port.in.ResetPasswordUseCase;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.identity.infrastructure.security.CurrentUserPrincipal;
import com.milestoneflow.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for password management endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /auth/password/change} — requires authentication</li>
 *   <li>{@code POST /auth/password/forgot} — public</li>
 *   <li>{@code POST /auth/password/reset} — public</li>
 * </ul>
 *
 * <p>Per B1 Baseline §14:
 * <ul>
 *   <li>Password change returns 200 with cleared cookies</li>
 *   <li>Forgot password always returns 200 (anti-enumeration)</li>
 *   <li>Password reset returns 200 with cleared cookies</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthPasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final AuthCookieWriter cookieWriter;

    public AuthPasswordController(ChangePasswordUseCase changePasswordUseCase,
                                  ForgotPasswordUseCase forgotPasswordUseCase,
                                  ResetPasswordUseCase resetPasswordUseCase,
                                  AuthCookieWriter cookieWriter) {
        this.changePasswordUseCase = changePasswordUseCase;
        this.forgotPasswordUseCase = forgotPasswordUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * Changes the authenticated user's password.
     *
     * <p>Per B1 Baseline §3: all sessions are revoked on password change.
     * Cookies are cleared because the current session is now invalid.
     */
    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<PasswordChangeResponse>> changePassword(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        ChangePasswordCommand command = new ChangePasswordCommand(
                principal.userId(),
                request.currentPassword(),
                request.newPassword()
        );

        changePasswordUseCase.changePassword(command);

        return ResponseEntity.ok()
                .header("Set-Cookie", cookieWriter.buildClearAccessCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearRefreshCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearXsrfCookie().toString())
                .body(ApiResponse.of(new PasswordChangeResponse(true), resolveRequestId()));
    }

    /**
     * Requests a password reset for the given email.
     *
     * <p>Per B1 Baseline §10: always returns 200 regardless of whether
     * the email exists to prevent account enumeration.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        ForgotPasswordCommand command = new ForgotPasswordCommand(request.email());
        forgotPasswordUseCase.forgotPassword(command);

        return ResponseEntity.ok(
                ApiResponse.of(new ForgotPasswordResponse(true), resolveRequestId()));
    }

    /**
     * Resets the user's password using a valid reset token.
     *
     * <p>Per B1 Baseline §9: all sessions are revoked on successful reset.
     * Cookies are cleared if the request carries old session cookies.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        ResetPasswordCommand command = new ResetPasswordCommand(
                request.token(),
                request.newPassword()
        );

        resetPasswordUseCase.resetPassword(command);

        return ResponseEntity.ok()
                .header("Set-Cookie", cookieWriter.buildClearAccessCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearRefreshCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearXsrfCookie().toString())
                .body(ApiResponse.of(new ResetPasswordResponse(true), resolveRequestId()));
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
