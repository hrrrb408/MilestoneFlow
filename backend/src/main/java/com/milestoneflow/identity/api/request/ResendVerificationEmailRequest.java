package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Resend verification email request DTO.
 */
public record ResendVerificationEmailRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must not exceed 320 characters")
        String email
) {
}
