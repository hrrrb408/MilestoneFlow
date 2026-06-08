package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for password reset confirmation.
 *
 * <p>toString() is overridden to prevent token and password leakage in logs.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "Reset token is required")
        String token,

        @NotBlank(message = "New password is required")
        String newPassword
) {

    @Override
    public String toString() {
        return "ResetPasswordRequest{token=[REDACTED], newPassword=[REDACTED]}";
    }
}
