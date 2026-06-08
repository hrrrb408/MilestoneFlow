package com.milestoneflow.identity.api;

import com.milestoneflow.identity.application.port.in.LogoutUseCase;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthLogoutController.class, IdentityExceptionHandler.class})
@Import({SecurityConfiguration.class, AuthCookieWriter.class, AuthenticationEntryPointImpl.class, IdentityConfiguration.class, TimeConfiguration.class})
@DisplayName("AuthLogoutController")
class AuthLogoutControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LogoutUseCase logoutUseCase;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AuthSessionRepository authSessionRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.AppUserRepository appUserRepository;
    @MockitoBean private com.milestoneflow.identity.application.port.out.TokenHasher tokenHasher;

    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");
    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
    private static final String RAW_ACCESS_TOKEN = "test-access-token-for-logout";
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
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        @DisplayName("successful logout returns 204 with cleared cookies")
        void success() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/logout")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN)))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertAllCookiesCleared(result);
        }

        @Test
        @DisplayName("already revoked session returns 401")
        void alreadyRevoked() throws Exception {
            willThrow(new AuthSessionRevokedException())
                    .given(logoutUseCase).logout(any(UUID.class));

            mockMvc.perform(post("/auth/logout")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout-all")
    class LogoutAll {

        @Test
        @DisplayName("successful logout-all returns 204 with cleared cookies")
        void success() throws Exception {
            MvcResult result = mockMvc.perform(post("/auth/logout-all")
                            .cookie(new jakarta.servlet.http.Cookie("MF_ACCESS", RAW_ACCESS_TOKEN)))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertAllCookiesCleared(result);
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/auth/logout-all"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
