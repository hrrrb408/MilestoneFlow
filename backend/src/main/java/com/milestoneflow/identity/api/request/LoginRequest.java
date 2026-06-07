package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request DTO.
 *
 * <p>Password is never included in toString() to prevent credential leakage.
 */
public final class LoginRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid email address")
    @Size(max = 320, message = "email must not exceed 320 characters")
    private String email;

    @NotBlank(message = "password must not be blank")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginRequest{email='" + email + "', password=[REDACTED]}";
    }
}
