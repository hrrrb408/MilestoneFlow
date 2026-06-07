package com.milestoneflow.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestoneflow.identity.api.request.ConfirmEmailVerificationRequest;
import com.milestoneflow.identity.api.request.RegisterRequest;
import com.milestoneflow.identity.api.request.ResendVerificationEmailRequest;
import com.milestoneflow.identity.application.command.ConfirmEmailVerificationCommand;
import com.milestoneflow.identity.application.command.RegisterUserCommand;
import com.milestoneflow.identity.application.command.ResendVerificationEmailCommand;
import com.milestoneflow.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.milestoneflow.identity.application.port.in.RegisterUserUseCase;
import com.milestoneflow.identity.application.port.in.ResendVerificationEmailUseCase;
import com.milestoneflow.identity.application.result.EmailVerificationResult;
import com.milestoneflow.identity.application.result.RegistrationResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.exception.VerificationTokenInvalidException;
import com.milestoneflow.identity.domain.type.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthRegistrationController.class)
@DisplayName("AuthRegistrationController")
class AuthRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @MockBean
    private ResendVerificationEmailUseCase resendVerificationEmailUseCase;

    @MockBean
    private ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 on successful registration")
        void returns201() throws Exception {
            UUID userId = UUID.randomUUID();
            when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                    .thenReturn(new RegistrationResult(userId, "user@example.com", UserStatus.PENDING_VERIFICATION));

            RegisterRequest request = new RegisterRequest("user@example.com", "Test User", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(userId.toString()))
                    .andExpect(jsonPath("$.data.email").value("user@example.com"))
                    .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                    .andExpect(jsonPath("$.meta.requestId").exists());
        }

        @Test
        @DisplayName("response contains no password or token")
        void noSensitiveFields() throws Exception {
            when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                    .thenReturn(new RegistrationResult(UUID.randomUUID(), "user@example.com", UserStatus.PENDING_VERIFICATION));

            RegisterRequest request = new RegisterRequest("user@example.com", "Test User", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.token").doesNotExist())
                    .andExpect(jsonPath("$.data.tokenHash").doesNotExist());
        }

        @Test
        @DisplayName("returns 422 for invalid email")
        void invalidEmail() throws Exception {
            RegisterRequest request = new RegisterRequest("not-an-email", "Test User", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("returns 422 for empty displayName")
        void emptyDisplayName() throws Exception {
            RegisterRequest request = new RegisterRequest("user@example.com", "", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("returns 422 for short password")
        void shortPassword() throws Exception {
            RegisterRequest request = new RegisterRequest("user@example.com", "Test User", "short");

            when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                    .thenThrow(new com.milestoneflow.identity.domain.policy.PasswordPolicyViolation(
                            "Password must be at least 8 characters"));

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_POLICY_VIOLATION"));
        }

        @Test
        @DisplayName("returns 400 for malformed JSON")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content("{invalid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("returns 409 for duplicate email")
        void duplicateEmail() throws Exception {
            when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                    .thenThrow(new EmailAlreadyExistsException());

            RegisterRequest request = new RegisterRequest("duplicate@example.com", "Test User", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("requestId in header matches body")
        void requestIdConsistency() throws Exception {
            String requestId = UUID.randomUUID().toString();
            when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                    .thenReturn(new RegistrationResult(UUID.randomUUID(), "user@example.com", UserStatus.PENDING_VERIFICATION));

            RegisterRequest request = new RegisterRequest("user@example.com", "Test User", "password123");

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", requestId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.requestId").value(requestId));
        }
    }

    @Nested
    @DisplayName("POST /auth/email-verification/resend")
    class Resend {

        @Test
        @DisplayName("returns 202 for known email")
        void knownEmail() throws Exception {
            doNothing().when(resendVerificationEmailUseCase).resend(any(ResendVerificationEmailCommand.class));

            ResendVerificationEmailRequest request = new ResendVerificationEmailRequest("user@example.com");

            mockMvc.perform(post("/auth/email-verification/resend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("returns 202 for unknown email (anti-enumeration)")
        void unknownEmail() throws Exception {
            doNothing().when(resendVerificationEmailUseCase).resend(any(ResendVerificationEmailCommand.class));

            ResendVerificationEmailRequest request = new ResendVerificationEmailRequest("unknown@example.com");

            mockMvc.perform(post("/auth/email-verification/resend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("returns 422 for invalid email")
        void invalidEmail() throws Exception {
            ResendVerificationEmailRequest request = new ResendVerificationEmailRequest("not-an-email");

            mockMvc.perform(post("/auth/email-verification/resend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("POST /auth/email-verification/confirm")
    class Confirm {

        @Test
        @DisplayName("returns 200 on successful verification")
        void returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            when(confirmEmailVerificationUseCase.confirm(any(ConfirmEmailVerificationCommand.class)))
                    .thenReturn(new EmailVerificationResult(userId, "user@example.com", UserStatus.ACTIVE));

            ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest("valid-token");

            mockMvc.perform(post("/auth/email-verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(userId.toString()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("returns 422 for empty token")
        void emptyToken() throws Exception {
            ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest("");

            mockMvc.perform(post("/auth/email-verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("returns 422 for invalid token")
        void invalidToken() throws Exception {
            when(confirmEmailVerificationUseCase.confirm(any(ConfirmEmailVerificationCommand.class)))
                    .thenThrow(new VerificationTokenInvalidException("token_not_found"));

            ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest("invalid-token");

            mockMvc.perform(post("/auth/email-verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_TOKEN_INVALID_OR_EXPIRED"))
                    .andExpect(jsonPath("$.message").doesNotHaveValue("invalid-token"));
        }

        @Test
        @DisplayName("token does not appear in response")
        void tokenNotInResponse() throws Exception {
            UUID userId = UUID.randomUUID();
            when(confirmEmailVerificationUseCase.confirm(any(ConfirmEmailVerificationCommand.class)))
                    .thenReturn(new EmailVerificationResult(userId, "user@example.com", UserStatus.ACTIVE));

            ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest("secret-token");

            mockMvc.perform(post("/auth/email-verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").doesNotExist())
                    .andExpect(jsonPath("$.data.tokenHash").doesNotExist());
        }

        @Test
        @DisplayName("returns 403 for disabled account")
        void disabledAccount() throws Exception {
            when(confirmEmailVerificationUseCase.confirm(any(ConfirmEmailVerificationCommand.class)))
                    .thenThrow(new AccountDisabledException());

            ConfirmEmailVerificationRequest request = new ConfirmEmailVerificationRequest("some-token");

            mockMvc.perform(post("/auth/email-verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", UUID.randomUUID().toString())
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
        }
    }
}
