package com.milestoneflow.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds Cache-Control: no-store and Pragma: no-cache headers to
 * authentication-related responses to prevent sensitive data from
 * being cached by browsers or intermediaries.
 *
 * <p>Per B1 Baseline §18 and security best practices, auth endpoints
 * must never be cached.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isAuthEndpoint(path)) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String path) {
        return path != null && (
                path.contains("/auth/login")
                || path.contains("/auth/refresh")
                || path.contains("/auth/logout")
                || path.contains("/auth/me")
                || path.contains("/auth/password/")
        );
    }
}
