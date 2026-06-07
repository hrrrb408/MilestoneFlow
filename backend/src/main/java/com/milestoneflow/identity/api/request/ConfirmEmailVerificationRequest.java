package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Email verification confirmation request DTO.
 *
 * <p>The token field contains the raw verification token.
 * toString() is overridden to prevent token leakage.
 */
public final class ConfirmEmailVerificationRequest {

    @NotBlank(message = "token must not be blank")
    private String token;

    public ConfirmEmailVerificationRequest() {
    }

    public ConfirmEmailVerificationRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "ConfirmEmailVerificationRequest{token=[REDACTED]}";
    }
}
