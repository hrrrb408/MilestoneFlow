package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.command.RefreshTokenCommand;
import com.milestoneflow.identity.application.port.in.RefreshTokenUseCase;
import com.milestoneflow.identity.application.result.RefreshTokenResult;
import com.milestoneflow.identity.infrastructure.config.AuthCookieProperties;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.util.Map;

/**
 * REST controller for the refresh token endpoint.
 *
 * <p>Refresh reads the {@code MF_REFRESH} cookie, rotates the token pair,
 * and sets new {@code MF_ACCESS} and {@code MF_REFRESH} cookies.
 * The response body never contains tokens or hashes.
 *
 * <p>Does not accept tokens from body, query string, or Authorization header.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Token refresh and rotation")
public class AuthRefreshController {

    private final RefreshTokenUseCase refreshTokenUseCase;
    private final AuthCookieWriter cookieWriter;
    private final AuthCookieProperties cookieProperties;

    public AuthRefreshController(RefreshTokenUseCase refreshTokenUseCase,
                                 AuthCookieWriter cookieWriter,
                                 AuthCookieProperties cookieProperties) {
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.cookieWriter = cookieWriter;
        this.cookieProperties = cookieProperties;
    }

    /**
     * Refreshes the authentication session by rotating tokens.
     *
     * <p>Reads MF_REFRESH cookie only. No body, no query params, no headers.
     */
    @Operation(summary = "Refresh access token",
            description = "Reads the MF_REFRESH HttpOnly cookie and rotates both "
                    + "access and refresh tokens. Sets new MF_ACCESS and MF_REFRESH cookies. "
                    + "Replay of a previously-used refresh token revokes the entire session family.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Tokens rotated — new cookies set"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Refresh token missing, invalid, expired, or reused",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(HttpServletRequest request) {
        // 1. Read refresh token from cookie
        Cookie refreshCookie = WebUtils.getCookie(request, cookieProperties.refreshName());
        if (refreshCookie == null || refreshCookie.getValue() == null
                || refreshCookie.getValue().isBlank()) {
            throw new com.milestoneflow.identity.application.exception.RefreshTokenMissingException();
        }

        // 2. Call use case
        RefreshTokenResult result = refreshTokenUseCase.refresh(
                new RefreshTokenCommand(refreshCookie.getValue()));

        // 3. Set new cookies
        return ResponseEntity.ok()
                .header("Set-Cookie", cookieWriter.buildAccessCookie(
                        result.rawAccessToken().value()).toString())
                .header("Set-Cookie", cookieWriter.buildRefreshCookie(
                        result.rawRefreshToken().value()).toString())
                .body(ApiResponse.of(Map.of("authenticated", true), resolveRequestId()));
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
