package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.port.in.LogoutUseCase;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for logout endpoints.
 *
 * <p>Both endpoints require authentication. On success, they return 204
 * with Set-Cookie headers that clear MF_ACCESS, MF_REFRESH, and XSRF-TOKEN.
 *
 * <p>Response body is intentionally empty per B1 Baseline §14.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Session logout and revocation")
public class AuthLogoutController {

    private final LogoutUseCase logoutUseCase;
    private final AuthCookieWriter cookieWriter;

    public AuthLogoutController(LogoutUseCase logoutUseCase, AuthCookieWriter cookieWriter) {
        this.logoutUseCase = logoutUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * Revokes the current session and clears cookies.
     *
     * <p>Per B1 Baseline §14: returns 204 No Content on success.
     */
    @Operation(summary = "Logout current session",
            description = "Revokes the current session and clears all auth cookies "
                    + "(MF_ACCESS, MF_REFRESH, XSRF-TOKEN). Requires authentication.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
                    description = "Logged out — cookies cleared"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        logoutUseCase.logout(principal.sessionId());

        return ResponseEntity.noContent()
                .header("Set-Cookie", cookieWriter.buildClearAccessCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearRefreshCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearXsrfCookie().toString())
                .build();
    }

    /**
     * Revokes all active sessions for the user and clears cookies.
     *
     * <p>Per B1 Baseline §14.2: returns 204 No Content on success.
     * Idempotent — succeeds even if no active sessions remain.
     */
    @Operation(summary = "Logout all sessions",
            description = "Revokes all active sessions for the user and clears all auth cookies. "
                    + "Idempotent — succeeds even if no active sessions remain. Requires authentication.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
                    description = "All sessions revoked — cookies cleared"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated")
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        logoutUseCase.logoutAll(principal.userId());

        return ResponseEntity.noContent()
                .header("Set-Cookie", cookieWriter.buildClearAccessCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearRefreshCookie().toString())
                .header("Set-Cookie", cookieWriter.buildClearXsrfCookie().toString())
                .build();
    }
}
