package com.milestoneflow.workspace.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for the current user's membership in a workspace.
 *
 * <p>Carries only the caller's own role and status — no email/displayName and
 * no sensitive fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The current user's membership in a workspace")
public record CurrentWorkspaceMembershipResponse(

        @Schema(description = "Workspace identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String workspaceId,

        @Schema(description = "The calling user's identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String userId,

        @Schema(description = "Membership role", example = "OWNER")
        String role,

        @Schema(description = "Membership status", example = "ACTIVE")
        String status,

        @Schema(description = "When the membership became active")
        String joinedAt
) {
}
