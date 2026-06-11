package com.milestoneflow.identity.api;

import com.milestoneflow.identity.api.request.LoginRequest;
import com.milestoneflow.identity.api.response.LoginResponse;
import com.milestoneflow.identity.application.command.LoginCommand;
import com.milestoneflow.identity.application.port.in.LoginUseCase;
import com.milestoneflow.identity.application.result.LoginResult;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the login endpoint.
 *
 * <p>Login creates an auth session and sets HttpOnly cookies (MF_ACCESS, MF_REFRESH).
 * The response body contains user data but never tokens or hashes.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login and session management")
public class AuthLoginController {

    private final LoginUseCase loginUseCase;
    private final AuthCookieWriter cookieWriter;

    public AuthLoginController(LoginUseCase loginUseCase,
                               AuthCookieWriter cookieWriter) {
        this.loginUseCase = loginUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * Authenticates a user and creates a session.
     *
     * <p>Sets MF_ACCESS, MF_REFRESH, and XSRF-TOKEN cookies.
     * Response body contains user info but never tokens.
     */
    @Operation(summary = "Login",
            description = "Authenticates a user with email and password. "
                    + "On success, sets HttpOnly cookies (MF_ACCESS, MF_REFRESH) and XSRF-TOKEN. "
                    + "Rate limited to 5 requests/15min.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Login successful — cookies set"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid credentials or account disabled",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Email not verified",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Rate limited",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginCommand command = new LoginCommand(request.getEmail(), request.getPassword());

        LoginResult result = loginUseCase.login(command);

        LoginResponse response = new LoginResponse(
                result.userId().toString(),
                result.email(),
                result.displayName(),
                result.status()
        );

        return ResponseEntity.ok()
                .header("Set-Cookie", cookieWriter.buildAccessCookie(
                        result.rawAccessToken().value()).toString())
                .header("Set-Cookie", cookieWriter.buildRefreshCookie(
                        result.rawRefreshToken().value()).toString())
                .body(ApiResponse.of(response, resolveRequestId()));
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
