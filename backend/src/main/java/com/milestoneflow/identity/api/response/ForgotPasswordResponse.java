package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for forgot password.
 *
 * <p>Always returns the same response regardless of whether the email exists
 * to prevent account enumeration per B1 Baseline §10.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForgotPasswordResponse(boolean accepted) {
}
