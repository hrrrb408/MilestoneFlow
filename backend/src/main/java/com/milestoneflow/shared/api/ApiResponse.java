package com.milestoneflow.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified success response envelope matching ADR-BE-009.
 *
 * <p>All API endpoints return this envelope. The {@code data} field carries the
 * resource or action result; {@code meta} carries request-level metadata and
 * optional pagination information.
 *
 * <p>Pagination fields appear directly inside {@code meta} (flat, not nested)
 * as specified in the ADR-BE-009 review report.
 *
 * <p>204 No Content responses do <strong>not</strong> use this envelope —
 * they have no response body at all.
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

    /**
     * Create a paginated response envelope.
     *
     * @param data      list content for the current page
     * @param requestId request identifier from MDC or header
     * @param pageMeta  pagination metadata
     * @param <T>       list element type
     * @return paginated API response
     */
    public static <T> ApiResponse<T> paged(T data, String requestId, PageMeta pageMeta) {
        return new ApiResponse<>(data, new Meta(
                requestId,
                pageMeta.page(),
                pageMeta.size(),
                pageMeta.totalElements(),
                pageMeta.totalPages(),
                pageMeta.hasNext()
        ));
    }

    /**
     * Response metadata carrying request identification and optional pagination.
     *
     * <p>For non-paginated responses, pagination fields are {@code null} and
     * omitted from JSON output via {@code @JsonInclude(NON_NULL)}.
     *
     * @param requestId    unique request identifier (always present)
     * @param page         current page number (null for non-paginated)
     * @param size         page size (null for non-paginated)
     * @param totalElements total elements across all pages (null for non-paginated)
     * @param totalPages   total number of pages (null for non-paginated)
     * @param hasNext      whether a subsequent page exists (null for non-paginated)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(
            String requestId,
            Integer page,
            Integer size,
            Long totalElements,
            Integer totalPages,
            Boolean hasNext
    ) {
        public Meta(String requestId) {
            this(requestId, null, null, null, null, null);
        }
    }
}
