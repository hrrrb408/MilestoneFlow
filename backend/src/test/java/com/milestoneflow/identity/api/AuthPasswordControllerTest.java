package com.milestoneflow.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestoneflow.identity.application.port.in.ChangePasswordUseCase;
import com.milestoneflow.identity.application.port.in.ForgotPasswordUseCase;
import com.milestoneflow.identity.application.port.in.ResetPasswordUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.InvalidCredentialsException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenExpiredException;
import com.milestoneflow.identity.domain.exception.PasswordResetTokenInvalidException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.policy.PasswordPolicyViolation;
import com.milestoneflow.identity.infrastructure.config.IdentityConfiguration;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.identity.infrastructure.security.AuthenticationEntryPointImpl;
import com.milestoneflow.identity.infrastructure.security.SecurityConfiguration;
import com.milestoneflow.shared.time.TimeConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthPasswordController.class, IdentityExceptionHandler.class})
@Import({SecurityConfiguration.class, AuthCookieWriter.class, AuthenticationEntryPointImpl.class,
        IdentityConfiguration.class, TimeConfiguration.class})
@DisplayName("AuthPasswordController")
class AuthPasswordControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
    @MockitoBean private ForgotPasswordUseCase forgotPasswordUseCase;
    @MockitoBean private ResetPasswordUseCase resetPasswordUseCase;
    @MockitoBean private AuthSessionRepository authSessionRepository;
    @MockitoBean private AppUserRepository appUserRepository;
    @MockitoBean private TokenHasher tokenHasher;

    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");
    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
    private static final String RAW_ACCESS_TOKEN = "test-access-token-for-password";
    private static final String ACCESS_TOKEN_HASH = "a".repeat(64);

    @BeforeEach
    void setUpAuth() {
        given(tokenHasher.hash(RAW_ACCESS_TOKEN)).willReturn(ACCESS_TOKEN_HASH);

        AuthSession session = AuthSession.create(SESSION_ID, USER_ID,
                ACCESS_TOKEN_HASH, "b".repeat(64), FAMILY_ID,
                0, NOW.plus(Duration.ofMinutes(15)), NOW.plus(Duration.ofDays(30)),
                null, null);
        given(authSessionRepository.findByAccessTokenHash(ACCESS_TOKEN_HASH))
                .willReturn(Optional.of(session));

        AppUser user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        user.activateAfterEmailVerification(NOW);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private void assertAllCookiesCleared(MvcResult result) {
        var setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).isNotNull();
        assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_ACCESS="))).isTrue();
        assertThat(setCookies.stream().anyMatch(c -> c.startsWith("MF_REFRESH="))).isTrue();
        assertThat(setCookies.stream().anyMatch(c -> c.startsWith("XSRF-TOKEN="))).isTrue();
    }

    @Nested
    @DisplayName("POST /auth/password/change")
    class ChangePassword {

        @Test
        @DisplayName("unauthenticated request returns 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/auth/password/change"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("successful change returns 200 with cleared cookies")
        void success() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passwordChanged").value(true))
                    .andReturn();

            assertAllCookiesCleared(result);
        }

        @Test
        @DisplayName("empty currentPassword returns 422")
        void emptyCurrentPassword() throws Exception {
            mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("empty newPassword returns 422")
        void emptyNewPassword() throws Exception {
            mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("wrong current password returns 401")
        void wrongCurrentPassword() throws Exception {
            willThrow(new InvalidCredentialsException())
                    .given(changePasswordUseCase).changePassword(any());

            mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("new password policy violation returns 422")
        void passwordPolicyViolation() throws Exception {
            willThrow(new PasswordPolicyViolation("Password too short"))
                    .given(changePasswordUseCase).changePassword(any());

            mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"short\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_POLICY_VIOLATION"));
        }

        @Test
        @DisplayName("response does not contain token or password hash")
        void noTokenOrHashInResponse() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/password/change")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isOk())
                            .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("accessToken");
            assertThat(body).doesNotContain("refreshToken");
            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("sessionId");
        }
    }

    @Nested
    @DisplayName("POST /auth/password/forgot")
    class ForgotPassword {

        @Test
        @DisplayName("known email returns 200 with accepted=true")
        void knownEmail() throws Exception {
            mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accepted").value(true));
        }

        @Test
        @DisplayName("unknown email returns same response (anti-enumeration)")
        void unknownEmail() throws Exception {
            mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"unknown@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accepted").value(true));
        }

        @Test
        @DisplayName("invalid email returns 422")
        void invalidEmail() throws Exception {
            mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("empty email returns 422")
        void emptyEmail() throws Exception {
            mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("response does not contain user status or reset token")
        void noStatusOrToken() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\"}"))
                    .andExpect(status().isOk())
                            .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("token");
            assertThat(body).doesNotContain("status");
            assertThat(body).doesNotContain("userId");
        }

        @Test
        @DisplayName("request ID present in response")
        void requestIdPresent() throws Exception {
            mockMvc.perform(post("/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.requestId").exists());
        }
    }

    @Nested
    @DisplayName("POST /auth/password/reset")
    class ResetPassword {

        @Test
        @DisplayName("successful reset returns 200 with cleared cookies")
        void success() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"valid-reset-token\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passwordReset").value(true))
                    .andReturn();

            assertAllCookiesCleared(result);
        }

        @Test
        @DisplayName("empty token returns 422")
        void emptyToken() throws Exception {
            mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("empty newPassword returns 422")
        void emptyNewPassword() throws Exception {
            mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"some-token\",\"newPassword\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("invalid token returns 401")
        void invalidToken() throws Exception {
            willThrow(new PasswordResetTokenInvalidException())
                    .given(resetPasswordUseCase).resetPassword(any());

            mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"invalid-token\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("expired token returns 401")
        void expiredToken() throws Exception {
            willThrow(new PasswordResetTokenExpiredException())
                    .given(resetPasswordUseCase).resetPassword(any());

            mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"expired-token\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("response does not contain token or hash")
        void noTokenOrHash() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"valid-token\",\"newPassword\":\"new-pass-123\"}"))
                    .andExpect(status().isOk())
                            .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("tokenHash");
            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("sessionId");
        }
    }
}
