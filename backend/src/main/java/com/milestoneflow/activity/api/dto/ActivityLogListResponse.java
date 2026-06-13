package com.milestoneflow.activity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO wrapping a list of activity log entries.
 *
 * <p>The {@code nextCursor} field is reserved for future cursor-based
 * pagination (B7-002). In B7-001 it is always {@code null}, and clients
 * should use the {@code limit} parameter for simple pagination.
 */
@Schema(description = "Paginated list of activity log entries")
public record ActivityLogListResponse(

        @Schema(description = "Activity log entries, ordered by createdAt DESC")
        List<ActivityLogResponse> items,

        @Schema(description = "Cursor for the next page (reserved for future use, always null in B7-001)",
                nullable = true)
        String nextCursor
) {
}
