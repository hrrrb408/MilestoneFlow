package com.milestoneflow.identity.api;

import com.milestoneflow.identity.api.request.LoginRequest;
import com.milestoneflow.identity.api.response.LoginResponse;
import com.milestoneflow.identity.application.command.LoginCommand;
import com.milestoneflow.identity.application.port.in.LoginUseCase;
import com.milestoneflow.identity.application.result.LoginResult;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.shared.api.ApiResponse;
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
