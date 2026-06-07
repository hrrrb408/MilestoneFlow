package com.milestoneflow.identity.api;

import com.milestoneflow.identity.api.request.ConfirmEmailVerificationRequest;
import com.milestoneflow.identity.api.request.RegisterRequest;
import com.milestoneflow.identity.api.request.ResendVerificationEmailRequest;
import com.milestoneflow.identity.api.response.EmailVerificationResponse;
import com.milestoneflow.identity.api.response.RegistrationResponse;
import com.milestoneflow.identity.application.command.ConfirmEmailVerificationCommand;
import com.milestoneflow.identity.application.command.RegisterUserCommand;
import com.milestoneflow.identity.application.command.ResendVerificationEmailCommand;
import com.milestoneflow.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.milestoneflow.identity.application.port.in.RegisterUserUseCase;
import com.milestoneflow.identity.application.port.in.ResendVerificationEmailUseCase;
import com.milestoneflow.identity.application.result.EmailVerificationResult;
import com.milestoneflow.identity.application.result.RegistrationResult;
import com.milestoneflow.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication registration and email verification endpoints.
 *
 * <p>All endpoints are under {@code /auth} which resolves to {@code /api/v1/auth}
 * via the server servlet context path.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/auth")
public class AuthRegistrationController {

    private final RegisterUserUseCase registerUserUseCase;
    private final ResendVerificationEmailUseCase resendVerificationEmailUseCase;
    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    public AuthRegistrationController(RegisterUserUseCase registerUserUseCase,
                                      ResendVerificationEmailUseCase resendVerificationEmailUseCase,
                                      ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.resendVerificationEmailUseCase = resendVerificationEmailUseCase;
        this.confirmEmailVerificationUseCase = confirmEmailVerificationUseCase;
    }

    /**
     * Registers a new user.
     *
     * <p>Creates a user in PENDING_VERIFICATION state and sends a verification email.
     * Rate limit hook deferred to MF-BE-011.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterUserCommand command = new RegisterUserCommand(
                request.getEmail(),
                request.getDisplayName(),
                request.getPassword()
        );

        RegistrationResult result = registerUserUseCase.register(command);

        RegistrationResponse response = new RegistrationResponse(
                result.userId().toString(),
                result.email(),
                result.status()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(response, resolveRequestId()));
    }

    /**
     * Resends a verification email.
     *
     * <p>Always returns 202 Accepted regardless of whether the email exists
     * or the account status to prevent account enumeration.
     * Rate limit hook deferred to MF-BE-011.
     */
    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponse<Object>> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request) {

        ResendVerificationEmailCommand command = new ResendVerificationEmailCommand(
                request.email()
        );

        resendVerificationEmailUseCase.resend(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(
                        "If the account is eligible, a verification email will be sent.",
                        resolveRequestId()));
    }

    /**
     * Confirms email verification using the provided token.
     */
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> confirmEmailVerification(
            @Valid @RequestBody ConfirmEmailVerificationRequest request) {

        ConfirmEmailVerificationCommand command = new ConfirmEmailVerificationCommand(
                request.getToken()
        );

        EmailVerificationResult result = confirmEmailVerificationUseCase.confirm(command);

        EmailVerificationResponse response = new EmailVerificationResponse(
                result.userId().toString(),
                result.email(),
                result.status()
        );

        return ResponseEntity.ok(ApiResponse.of(response, resolveRequestId()));
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
