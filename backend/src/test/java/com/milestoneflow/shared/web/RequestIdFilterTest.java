package com.milestoneflow.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private RequestIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateUuidWhenNoHeaderProvided() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Request-Id"), argThat(id -> {
            UUID.fromString(id); // should not throw
            return true;
        }));
        // MDC should be cleared after filter (already removed by finally block)
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldAcceptValidUuidHeader() throws Exception {
        String existingId = UUID.randomUUID().toString();
        when(request.getHeader("X-Request-Id")).thenReturn(existingId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Request-Id", existingId);
    }

    @Test
    void shouldGenerateNewUuidForMalformedHeader() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("not-a-uuid");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Request-Id"), argThat(id -> {
            UUID.fromString(id); // should not throw
            return !id.equals("not-a-uuid");
        }));
    }

    @Test
    void shouldGenerateNewUuidForBlankHeader() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("   ");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Request-Id"), argThat(id -> {
            UUID.fromString(id);
            return true;
        }));
    }

    @Test
    void shouldSetMdcDuringFilterExecution() throws Exception {
        String existingId = UUID.randomUUID().toString();
        when(request.getHeader("X-Request-Id")).thenReturn(existingId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        doAnswer(invocation -> {
            // MDC should be set inside the filter chain
            assertThat(MDC.get("requestId")).isEqualTo(existingId);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // MDC should be cleaned up after filter
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldCleanUpMdcEvenWhenFilterChainThrows() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        doThrow(new RuntimeException("Simulated error"))
                .when(filterChain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
