package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.port.in.LoginUseCase;
import com.milestoneflow.identity.application.result.LoginResult;
import com.milestoneflow.identity.application.service.SecretToken;
import com.milestoneflow.identity.infrastructure.config.IdentityConfiguration;
import com.milestoneflow.identity.infrastructure.security.AuthCookieWriter;
import com.milestoneflow.identity.infrastructure.security.AuthenticationEntryPointImpl;
import com.milestoneflow.identity.infrastructure.security.SecurityConfiguration;
import com.milestoneflow.shared.time.TimeConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthLoginController.class, AuthMeController.class, IdentityExceptionHandler.class})
@Import({SecurityConfiguration.class, AuthCookieWriter.class, AuthenticationEntryPointImpl.class, IdentityConfiguration.class, TimeConfiguration.class})
@DisplayName("AuthLoginController")
class AuthLoginControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoginUseCase loginUseCase;
    @MockitoBean private com.milestoneflow.identity.application.port.in.GetCurrentUserUseCase getCurrentUserUseCase;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AuthSessionRepository authSessionRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AppUserRepository appUserRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.TokenHasher tokenHasher;

    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");

    private LoginResult createSuccessfulLoginResult() {
        return new LoginResult(USER_ID, "user@example.com", "Test User", "ACTIVE",
                new SecretToken("raw-access-token"), new SecretToken("raw-refresh-token"));
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("successful login returns 200")
        void success() throws Exception {
            given(loginUseCase.login(any())).willReturn(createSuccessfulLoginResult());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.data.email").value("user@example.com"))
                    .andExpect(jsonPath("$.data.displayName").value("Test User"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("login sets MF_ACCESS cookie")
        void setsAccessCookie() throws Exception {
            given(loginUseCase.login(any())).willReturn(createSuccessfulLoginResult());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(result -> {
                        String setCookie = result.getResponse().getHeader("Set-Cookie");
                        if (setCookie != null) {
                            boolean hasAccess = false;
                            for (String cookie : result.getResponse().getHeaders("Set-Cookie")) {
                                if (cookie.startsWith("MF_ACCESS=")) hasAccess = true;
                            }
                            assert hasAccess : "MF_ACCESS cookie not found";
                        }
                    });
        }

        @Test
        @DisplayName("login sets MF_REFRESH cookie")
        void setsRefreshCookie() throws Exception {
            given(loginUseCase.login(any())).willReturn(createSuccessfulLoginResult());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(result -> {
                        boolean hasRefresh = false;
                        for (String cookie : result.getResponse().getHeaders("Set-Cookie")) {
                            if (cookie.startsWith("MF_REFRESH=")) hasRefresh = true;
                        }
                        assert hasRefresh : "MF_REFRESH cookie not found";
                    });
        }

        @Test
        @DisplayName("response body does not contain tokens")
        void noTokensInBody() throws Exception {
            given(loginUseCase.login(any())).willReturn(createSuccessfulLoginResult());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.data.accessTokenHash").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshTokenHash").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("invalid email format returns 422")
        void invalidEmail() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\",\"password\":\"test-password\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("blank password returns 422")
        void blankPassword() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("malformed JSON returns 400")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("wrong credentials returns 401")
        void wrongCredentials() throws Exception {
            given(loginUseCase.login(any()))
                    .willThrow(new com.milestoneflow.identity.domain.exception.InvalidCredentialsException());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("unverified email returns 403")
        void unverifiedEmail() throws Exception {
            given(loginUseCase.login(any()))
                    .willThrow(new com.milestoneflow.identity.domain.exception.EmailNotVerifiedException());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("disabled account returns 401")
        void disabledAccount() throws Exception {
            given(loginUseCase.login(any()))
                    .willThrow(new com.milestoneflow.identity.domain.exception.AccountDisabledException());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"test-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class Me {

        @Test
        @DisplayName("without cookie returns 401")
        void withoutCookie() throws Exception {
            mockMvc.perform(get("/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
