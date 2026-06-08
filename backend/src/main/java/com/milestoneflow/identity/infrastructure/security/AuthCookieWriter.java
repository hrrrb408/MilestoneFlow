package com.milestoneflow.identity.infrastructure.security;

import com.milestoneflow.identity.infrastructure.config.AuthCookieProperties;
import com.milestoneflow.identity.infrastructure.config.AuthTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds authentication cookies for login and refresh responses.
 *
 * <p>Creates {@code MF_ACCESS}, {@code MF_REFRESH}, and {@code XSRF-TOKEN}
 * cookies per B1 Authentication Baseline §2.
 *
 * <p>Cookie values are never logged.
 */
@Component
public class AuthCookieWriter {

    private final AuthCookieProperties cookieProperties;
    private final AuthTokenProperties tokenProperties;

    public AuthCookieWriter(AuthCookieProperties cookieProperties,
                            AuthTokenProperties tokenProperties) {
        this.cookieProperties = cookieProperties;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Builds the access token cookie.
     *
     * @param rawAccessToken the raw opaque access token
     * @return ResponseCookie for MF_ACCESS
     */
    public ResponseCookie buildAccessCookie(String rawAccessToken) {
        return ResponseCookie.from(cookieProperties.accessName(), rawAccessToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSiteAccess())
                .path(cookieProperties.accessPath())
                .maxAge(tokenProperties.accessTokenTtl())
                .build();
    }

    /**
     * Builds the refresh token cookie.
     *
     * @param rawRefreshToken the raw opaque refresh token
     * @return ResponseCookie for MF_REFRESH
     */
    public ResponseCookie buildRefreshCookie(String rawRefreshToken) {
        return ResponseCookie.from(cookieProperties.refreshName(), rawRefreshToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSiteRefresh())
                .path(cookieProperties.refreshPath())
                .maxAge(tokenProperties.refreshTokenTtl())
                .build();
    }

    /**
     * Builds the XSRF-TOKEN cookie for CSRF protection.
     *
     * <p>This cookie is NOT HttpOnly so that JavaScript can read it
     * and send the value as an {@code X-XSRF-TOKEN} header.
     *
     * @param xsrfToken the XSRF token value
     * @return ResponseCookie for XSRF-TOKEN
     */
    public ResponseCookie buildXsrfCookie(String xsrfToken) {
        return ResponseCookie.from(cookieProperties.xsrfName(), xsrfToken)
                .httpOnly(false)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path(cookieProperties.accessPath())
                .maxAge(tokenProperties.refreshTokenTtl())
                .build();
    }

    /**
     * Builds an access cookie with Max-Age=0 to clear it on logout.
     *
     * @return ResponseCookie that clears MF_ACCESS
     */
    public ResponseCookie buildClearAccessCookie() {
        return ResponseCookie.from(cookieProperties.accessName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSiteAccess())
                .path(cookieProperties.accessPath())
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * Builds a refresh cookie with Max-Age=0 to clear it on logout.
     *
     * @return ResponseCookie that clears MF_REFRESH
     */
    public ResponseCookie buildClearRefreshCookie() {
        return ResponseCookie.from(cookieProperties.refreshName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSiteRefresh())
                .path(cookieProperties.refreshPath())
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * Builds an XSRF-TOKEN cookie with Max-Age=0 to clear it on logout.
     *
     * @return ResponseCookie that clears XSRF-TOKEN
     */
    public ResponseCookie buildClearXsrfCookie() {
        return ResponseCookie.from(cookieProperties.xsrfName(), "")
                .httpOnly(false)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path(cookieProperties.accessPath())
                .maxAge(Duration.ZERO)
                .build();
    }

    @Override
    public String toString() {
        return "AuthCookieWriter{accessName=" + cookieProperties.accessName()
                + ", refreshName=" + cookieProperties.refreshName()
                + ", xsrfName=" + cookieProperties.xsrfName() + "}";
    }
}
