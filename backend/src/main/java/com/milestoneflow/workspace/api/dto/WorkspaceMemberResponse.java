package com.milestoneflow.workspace.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a single workspace member.
 *
 * <p>Carries only safe display fields. Does not expose {@code passwordHash},
 * {@code emailNormalized}, {@code lastLoginAt}, or any token material.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single workspace member with safe display info")
public record WorkspaceMemberResponse(

        @Schema(description = "Member's user identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String userId,

        @Schema(description = "Member's email address", example = "owner@example.com")
        String email,

        @Schema(description = "Member's display name", example = "Owner")
        String displayName,

        @Schema(description = "Membership role", example = "OWNER")
        String role,

        @Schema(description = "Membership status", example = "ACTIVE")
        String status,

        @Schema(description = "When the membership became active")
        String joinedAt
) {
}
