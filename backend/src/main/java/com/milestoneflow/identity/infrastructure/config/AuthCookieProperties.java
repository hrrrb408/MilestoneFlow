package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for authentication cookie attributes.
 *
 * <p>Per B1 Authentication Baseline §2:
 * <ul>
 *   <li>{@code MF_ACCESS} — HttpOnly, SameSite=Lax, Path=/api/v1</li>
 *   <li>{@code MF_REFRESH} — HttpOnly, SameSite=Strict, Path=/api/v1/auth/refresh</li>
 *   <li>{@code XSRF-TOKEN} — non-HttpOnly, SameSite=Lax, Path=/api/v1</li>
 * </ul>
 *
 * <p>This configuration does not contain secrets.
 */
@ConfigurationProperties(prefix = "milestoneflow.auth.cookies")
public record AuthCookieProperties(
        String accessName,
        String refreshName,
        String xsrfName,
        String accessPath,
        String refreshPath,
        String sameSiteAccess,
        String sameSiteRefresh,
        boolean secure
) {

    private static final String DEFAULT_ACCESS_NAME = "MF_ACCESS";
    private static final String DEFAULT_REFRESH_NAME = "MF_REFRESH";
    private static final String DEFAULT_XSRF_NAME = "XSRF-TOKEN";
    private static final String DEFAULT_ACCESS_PATH = "/api/v1";
    private static final String DEFAULT_REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String DEFAULT_SAMESITE_ACCESS = "Lax";
    private static final String DEFAULT_SAMESITE_REFRESH = "Strict";

    public AuthCookieProperties {
        if (accessName == null || accessName.isBlank()) {
            accessName = DEFAULT_ACCESS_NAME;
        }
        if (refreshName == null || refreshName.isBlank()) {
            refreshName = DEFAULT_REFRESH_NAME;
        }
        if (xsrfName == null || xsrfName.isBlank()) {
            xsrfName = DEFAULT_XSRF_NAME;
        }
        if (accessPath == null || accessPath.isBlank()) {
            accessPath = DEFAULT_ACCESS_PATH;
        }
        if (refreshPath == null || refreshPath.isBlank()) {
            refreshPath = DEFAULT_REFRESH_PATH;
        }
        if (sameSiteAccess == null || sameSiteAccess.isBlank()) {
            sameSiteAccess = DEFAULT_SAMESITE_ACCESS;
        }
        if (sameSiteRefresh == null || sameSiteRefresh.isBlank()) {
            sameSiteRefresh = DEFAULT_SAMESITE_REFRESH;
        }
    }
}
