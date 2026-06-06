package com.milestoneflow.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified success response envelope matching architecture spec §9.2.
 *
 * <p>All API endpoints return this envelope. The {@code data} field carries the
 * resource or action result; {@code meta} carries request-level metadata.
 *
 * @param <T> the payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta) {

    public ApiResponse(T data, String requestId) {
        this(data, new Meta(requestId));
    }

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, requestId);
    }

    public record Meta(String requestId) {}
}
