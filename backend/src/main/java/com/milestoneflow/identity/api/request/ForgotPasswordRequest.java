package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for forgot password.
 *
 * <p>Email is validated but not normalized at this layer.
 * Normalization is handled by the application service.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {
}
