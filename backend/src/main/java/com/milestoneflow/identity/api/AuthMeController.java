package com.milestoneflow.identity.api;

import com.milestoneflow.identity.api.response.CurrentUserResponse;
import com.milestoneflow.identity.application.port.in.GetCurrentUserUseCase;
import com.milestoneflow.identity.application.result.CurrentUserResult;
import com.milestoneflow.identity.infrastructure.security.CurrentUserPrincipal;
import com.milestoneflow.shared.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the current user endpoint.
 *
 * <p>Requires a valid MF_ACCESS cookie. Returns the authenticated user's
 * safe, non-sensitive data.
 */
@RestController
@RequestMapping("/auth")
public class AuthMeController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthMeController(GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    /**
     * Returns the currently authenticated user.
     *
     * <p>Requires valid access cookie. Response never contains
     * password hash, token hash, or session internals.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {

        CurrentUserResult result = getCurrentUserUseCase.getCurrentUser(principal.userId());

        CurrentUserResponse response = new CurrentUserResponse(
                result.userId().toString(),
                result.email(),
                result.displayName(),
                result.status()
        );

        return ResponseEntity.ok(ApiResponse.of(response, resolveRequestId()));
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
