package com.milestoneflow.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request DTO.
 *
 * <p>Password is never included in toString() to prevent credential leakage.
 */
public final class RegisterRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid email address")
    @Size(max = 320, message = "email must not exceed 320 characters")
    private String email;

    @NotBlank(message = "displayName must not be blank")
    @Size(max = 100, message = "displayName must not exceed 100 characters")
    private String displayName;

    @NotBlank(message = "password must not be blank")
    private String password;

    public RegisterRequest() {
    }

    public RegisterRequest(String email, String displayName, String password) {
        this.email = email;
        this.displayName = displayName;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "RegisterRequest{email='" + email
                + "', displayName='" + displayName
                + "', password=[REDACTED]}";
    }
}
