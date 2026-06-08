package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for password change.
 *
 * <p>toString() is overridden to prevent password leakage in logs.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest{currentPassword=[REDACTED], newPassword=[REDACTED]}";
    }
}
