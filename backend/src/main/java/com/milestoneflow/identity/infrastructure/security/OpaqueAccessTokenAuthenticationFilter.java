package com.milestoneflow.identity.infrastructure.security;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.infrastructure.config.AuthCookieProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests by validating the opaque access token in the
 * {@code MF_ACCESS} cookie against the database.
 *
 * <p>Authentication flow:
 * <ol>
 *   <li>Read {@code MF_ACCESS} cookie</li>
 *   <li>If absent, continue unauthenticated</li>
 *   <li>Hash the raw token</li>
 *   <li>Look up {@code AuthSession} by access token hash</li>
 *   <li>Verify session is ACTIVE and not expired</li>
 *   <li>Load the user and verify they are ACTIVE</li>
 *   <li>Set {@link CurrentUserPrincipal} in SecurityContext</li>
 * </ol>
 *
 * <p>Security guarantees:
 * <ul>
 *   <li>Never logs raw tokens or token hashes</li>
 *   <li>Never uses the refresh token for authentication</li>
 *   <li>Never modifies the database</li>
 *   <li>Never writes cookies</li>
 *   <li>Never creates sessions</li>
 * </ul>
 */
@Component
public class OpaqueAccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(
            OpaqueAccessTokenAuthenticationFilter.class);

    private final AuthSessionRepository authSessionRepository;
    private final AppUserRepository appUserRepository;
    private final TokenHasher tokenHasher;
    private final AuthCookieProperties cookieProperties;
    private final Clock clock;

    public OpaqueAccessTokenAuthenticationFilter(AuthSessionRepository authSessionRepository,
                                                  AppUserRepository appUserRepository,
                                                  TokenHasher tokenHasher,
                                                  AuthCookieProperties cookieProperties,
                                                  Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.appUserRepository = appUserRepository;
        this.tokenHasher = tokenHasher;
        this.cookieProperties = cookieProperties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> rawToken = extractAccessToken(request);

        if (rawToken.isEmpty()) {
            // No cookie — continue unauthenticated
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(rawToken.get(), request);
        } catch (Exception e) {
            // Authentication failed — clear context and continue
            log.debug("Access token authentication failed");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String rawToken, HttpServletRequest request) {
        String tokenHash = tokenHasher.hash(rawToken);

        Optional<AuthSession> sessionOpt = authSessionRepository.findByAccessTokenHash(tokenHash);
        if (sessionOpt.isEmpty()) {
            log.debug("Access authentication failed: session not found");
            return;
        }

        AuthSession session = sessionOpt.get();

        if (session.getStatus() != AuthSessionStatus.ACTIVE) {
            log.debug("Access authentication failed: session status={}", session.getStatus());
            return;
        }

        Instant now = Instant.now(clock);
        if (session.isAccessExpired(now)) {
            log.debug("Access authentication failed: access token expired");
            return;
        }

        Optional<AppUser> userOpt = appUserRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            log.debug("Access authentication failed: user not found");
            return;
        }

        AppUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.debug("Access authentication failed: user status={}", user.getStatus());
            return;
        }

        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name(),
                session.getId(),
                session.getSessionFamilyId()
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> extractAccessToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, cookieProperties.accessName());
        if (cookie == null) {
            return Optional.empty();
        }
        String value = cookie.getValue();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
