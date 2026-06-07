package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.port.in.LoginUseCase;
import com.milestoneflow.identity.application.port.in.RefreshTokenUseCase;
import com.milestoneflow.identity.application.result.RefreshTokenResult;
import com.milestoneflow.identity.application.service.SecretToken;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException;
import com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenReusedException;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthRefreshController.class, IdentityExceptionHandler.class})
@Import({SecurityConfiguration.class, AuthCookieWriter.class, AuthenticationEntryPointImpl.class,
        IdentityConfiguration.class, TimeConfiguration.class})
@DisplayName("AuthRefreshController")
class AuthRefreshControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RefreshTokenUseCase refreshTokenUseCase;
    @MockitoBean private LoginUseCase loginUseCase;
    @MockitoBean private com.milestoneflow.identity.application.port.in.GetCurrentUserUseCase getCurrentUserUseCase;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AuthSessionRepository authSessionRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AppUserRepository appUserRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.TokenHasher tokenHasher;

    private RefreshTokenResult createSuccessfulRefreshResult() {
        return new RefreshTokenResult(
                new SecretToken("new-raw-access"), new SecretToken("new-raw-refresh"));
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        @DisplayName("successful refresh returns 200")
        void success() throws Exception {
            given(refreshTokenUseCase.refresh(any())).willReturn(createSuccessfulRefreshResult());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "raw-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.authenticated").value(true));
        }

        @Test
        @DisplayName("successful refresh sets new cookies")
        void setsNewCookies() throws Exception {
            given(refreshTokenUseCase.refresh(any())).willReturn(createSuccessfulRefreshResult());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "raw-refresh-token")))
                    .andExpect(result -> {
                        boolean hasAccess = false;
                        boolean hasRefresh = false;
                        for (String cookie : result.getResponse().getHeaders("Set-Cookie")) {
                            if (cookie.startsWith("MF_ACCESS=")) hasAccess = true;
                            if (cookie.startsWith("MF_REFRESH=")) hasRefresh = true;
                        }
                        assert hasAccess : "MF_ACCESS cookie not found";
                        assert hasRefresh : "MF_REFRESH cookie not found";
                    });
        }

        @Test
        @DisplayName("response body does not contain tokens")
        void noTokensInBody() throws Exception {
            given(refreshTokenUseCase.refresh(any())).willReturn(createSuccessfulRefreshResult());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "raw-refresh-token")))
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.data.accessTokenHash").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshTokenHash").doesNotExist())
                    .andExpect(jsonPath("$.data.sessionId").doesNotExist());
        }

        @Test
        @DisplayName("no refresh cookie returns 401 AUTH_UNAUTHENTICATED")
        void noRefreshCookie() throws Exception {
            mockMvc.perform(post("/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("empty refresh cookie returns 401 AUTH_UNAUTHENTICATED")
        void emptyRefreshCookie() throws Exception {
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("invalid refresh token returns 401")
        void invalidToken() throws Exception {
            given(refreshTokenUseCase.refresh(any()))
                    .willThrow(new RefreshTokenInvalidException());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "invalid-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("expired refresh token returns 401")
        void expiredToken() throws Exception {
            given(refreshTokenUseCase.refresh(any()))
                    .willThrow(new RefreshTokenExpiredException());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "expired-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("replayed token returns 401")
        void replayedToken() throws Exception {
            given(refreshTokenUseCase.refresh(any()))
                    .willThrow(new RefreshTokenReusedException());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "reused-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_REUSED"));
        }

        @Test
        @DisplayName("revoked session returns 401")
        void revokedSession() throws Exception {
            given(refreshTokenUseCase.refresh(any()))
                    .willThrow(new AuthSessionRevokedException());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "revoked-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
        }

        @Test
        @DisplayName("disabled account returns 401")
        void disabledAccount() throws Exception {
            given(refreshTokenUseCase.refresh(any()))
                    .willThrow(new AccountDisabledException());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "valid-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
        }

        @Test
        @DisplayName("response includes requestId")
        void includesRequestId() throws Exception {
            given(refreshTokenUseCase.refresh(any())).willReturn(createSuccessfulRefreshResult());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "raw-refresh-token")))
                    .andExpect(jsonPath("$.meta.requestId").exists());
        }
    }

    @Nested
    @DisplayName("Refresh endpoint security")
    class RefreshSecurity {

        @Test
        @DisplayName("does not accept body token")
        void doesNotAcceptBodyToken() throws Exception {
            // The controller reads only from cookie; body is ignored
            given(refreshTokenUseCase.refresh(any())).willReturn(createSuccessfulRefreshResult());

            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "cookie-token"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"body-token\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("does not accept query parameter token")
        void doesNotAcceptQueryParamToken() throws Exception {
            mockMvc.perform(post("/auth/refresh")
                            .param("refreshToken", "query-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("does not accept Authorization header token")
        void doesNotAcceptAuthHeaderToken() throws Exception {
            mockMvc.perform(post("/auth/refresh")
                            .header("Authorization", "Bearer some-token"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
