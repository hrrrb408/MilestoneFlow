package com.milestoneflow.identity.infrastructure.security;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.infrastructure.config.AuthCookieProperties;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpaqueAccessTokenAuthenticationFilter")
class OpaqueAccessTokenAuthenticationFilterTest {

    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private TokenHasher tokenHasher;

    private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");
    private static final UUID SESSION_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abd");
    private static final UUID FAMILY_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abe");
    private static final String TOKEN_HASH = "a".repeat(64);

    private OpaqueAccessTokenAuthenticationFilter filter;
    private AuthCookieProperties cookieProperties;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        cookieProperties = new AuthCookieProperties(
                "MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", false);
        filter = new OpaqueAccessTokenAuthenticationFilter(
                authSessionRepository, appUserRepository, tokenHasher,
                cookieProperties, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private MockHttpServletRequest createRequestWithAccessCookie(String value) {
        var request = new MockHttpServletRequest();
        request.setServerPort(8080);
        request.setRequestURI("/api/v1/auth/me");
        if (value != null) {
            var cookie = new jakarta.servlet.http.Cookie("MF_ACCESS", value);
            request.setCookies(cookie);
        }
        return request;
    }

    private AuthSession createActiveSession() {
        return AuthSession.create(SESSION_ID, USER_ID, TOKEN_HASH, "b".repeat(64),
                FAMILY_ID, 0, NOW.plus(Duration.ofMinutes(15)), NOW.plus(Duration.ofDays(30)),
                null, null);
    }

    private AppUser createActiveUser() {
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        user.activateAfterEmailVerification(NOW);
        return user;
    }

    @Test
    @DisplayName("no cookie → no authentication")
    void noCookie() throws ServletException, IOException {
        var request = createRequestWithAccessCookie(null);
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("invalid cookie hash → no authentication")
    void invalidCookieHash() throws ServletException, IOException {
        var request = createRequestWithAccessCookie("invalid-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("invalid-token")).thenReturn("x".repeat(64));
        when(authSessionRepository.findByAccessTokenHash("x".repeat(64))).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("session not found → no authentication")
    void sessionNotFound() throws ServletException, IOException {
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("REVOKED session → no authentication")
    void revokedSession() throws ServletException, IOException {
        var session = createActiveSession();
        session.revoke(NOW, "TEST");
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("expired access token → no authentication")
    void expiredAccessToken() throws ServletException, IOException {
        var session = AuthSession.create(SESSION_ID, USER_ID, TOKEN_HASH, "b".repeat(64),
                FAMILY_ID, 0, NOW.minus(Duration.ofMinutes(1)), NOW.plus(Duration.ofDays(30)),
                null, null);
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("user not found → no authentication")
    void userNotFound() throws ServletException, IOException {
        var session = createActiveSession();
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("DISABLED user → no authentication")
    void disabledUser() throws ServletException, IOException {
        var session = createActiveSession();
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        user.activateAfterEmailVerification(NOW);
        user.disable();
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("PENDING_VERIFICATION user → no authentication")
    void pendingUser() throws ServletException, IOException {
        var session = createActiveSession();
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        var request = createRequestWithAccessCookie("some-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        when(tokenHasher.hash("some-token")).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Nested
    @DisplayName("successful authentication")
    class SuccessfulAuthentication {

        @Test
        @DisplayName("ACTIVE user → authentication success")
        void activeUserSuccess() throws ServletException, IOException {
            var session = createActiveSession();
            var user = createActiveUser();
            var request = createRequestWithAccessCookie("valid-token");
            var response = new MockHttpServletResponse();
            var chain = new org.springframework.mock.web.MockFilterChain();

            when(tokenHasher.hash("valid-token")).thenReturn(TOKEN_HASH);
            when(authSessionRepository.findByAccessTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isInstanceOf(CurrentUserPrincipal.class);

            var principal = (CurrentUserPrincipal) auth.getPrincipal();
            assertThat(principal.userId()).isEqualTo(USER_ID);
            assertThat(principal.email()).isEqualTo("user@example.com");
            assertThat(principal.displayName()).isEqualTo("Test User");
            assertThat(principal.status()).isEqualTo("ACTIVE");
            assertThat(principal.sessionId()).isEqualTo(SESSION_ID);
            assertThat(principal.sessionFamilyId()).isEqualTo(FAMILY_ID);
        }
    }

    @Test
    @DisplayName("refresh cookie cannot authenticate")
    void refreshCookieNotUsed() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");
        request.setCookies(new jakarta.servlet.http.Cookie("MF_REFRESH", "some-refresh-token"));

        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenHasher, never()).hash(anyString());
    }
}
