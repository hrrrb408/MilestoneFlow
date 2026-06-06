package com.milestoneflow.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that manages the {@code X-Request-Id} header.
 *
 * <ul>
 *   <li>Accepts an existing {@code X-Request-Id} from the client if it is a valid UUID.</li>
 *   <li>Generates a new UUID if the header is missing or malformed.</li>
 *   <li>Places the request ID into MDC under key {@code "requestId"} for structured logging.</li>
 *   <li>Writes the request ID back into the response header {@code X-Request-Id}.</li>
 *   <li>Cleans up MDC in {@code finally} to prevent leakage across threads.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    static final String HEADER_NAME = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try {
            log.debug("Request started: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER_NAME);
        if (incoming != null && !incoming.isBlank()) {
            try {
                UUID.fromString(incoming.trim());
                return incoming.trim();
            } catch (IllegalArgumentException ignored) {
                log.debug("Invalid X-Request-Id received: '{}', generating a new one", incoming);
            }
        }
        return UUID.randomUUID().toString();
    }
}
