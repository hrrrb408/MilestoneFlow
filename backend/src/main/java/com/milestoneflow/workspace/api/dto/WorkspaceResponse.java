package com.milestoneflow.workspace.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for workspace operations.
 *
 * <p>Carries workspace data plus the current user's role.
 * Does not expose internal settings, archivedAt, version, or membership ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Workspace response with current user's role")
public record WorkspaceResponse(

        @Schema(description = "Workspace unique identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String id,

        @Schema(description = "Workspace display name", example = "My Workspace")
        String name,

        @Schema(description = "URL-friendly identifier", example = "my-workspace")
        String slug,

        @Schema(description = "Workspace status", example = "ACTIVE")
        String status,

        @Schema(description = "IANA timezone ID", example = "Asia/Taipei")
        String timezone,

        @Schema(description = "3-letter currency code", example = "TWD")
        String defaultCurrency,

        @Schema(description = "Current user's role in this workspace", example = "OWNER")
        String role,

        @Schema(description = "Workspace creation timestamp")
        String createdAt
) {
}
