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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "User registration, email verification, and resend")
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
     */
    @Operation(summary = "Register a new user",
            description = "Creates a new user account in PENDING_VERIFICATION state "
                    + "and sends a verification email. Rate limited to 10 requests/hour.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Rate limited",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @SecurityRequirements
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
     * <p>Always returns 200 Accepted regardless of whether the email exists
     * or the account status to prevent account enumeration.
     */
    @Operation(summary = "Resend verification email",
            description = "Resends the email verification link. Always returns 200 "
                    + "regardless of email existence to prevent account enumeration. "
                    + "Rate limited to 3 requests/15min.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Request accepted (email sent if eligible)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Rate limited",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponse<Object>> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request) {

        ResendVerificationEmailCommand command = new ResendVerificationEmailCommand(
                request.email()
        );

        resendVerificationEmailUseCase.resend(command);

        return ResponseEntity.ok(ApiResponse.of(
                        "If the account is eligible, a verification email will be sent.",
                        resolveRequestId()));
    }

    /**
     * Confirms email verification using the provided token.
     */
    @Operation(summary = "Confirm email verification",
            description = "Verifies the user's email address using the token from the "
                    + "verification email link. No authentication required.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Email verified successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Token invalid or expired",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @SecurityRequirements
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
