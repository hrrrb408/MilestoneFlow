package com.milestoneflow.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestoneflow.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Returns a 401 {@code AUTH_UNAUTHENTICATED} error for unauthenticated
 * requests to protected endpoints.
 *
 * <p>Does not expose token status, session details, or any internal information.
 * Returns the same response regardless of why authentication failed.
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuthenticationEntryPointImpl(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String requestId = resolveRequestId();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now(clock))
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .code("AUTH_UNAUTHENTICATED")
                .message("Authentication required")
                .requestId(requestId)
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
