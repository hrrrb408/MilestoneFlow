package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Current user response DTO.
 *
 * <p>Contains only safe, non-sensitive data.
 * No password hash, no token hash, no session internals.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CurrentUserResponse(
        String userId,
        String email,
        String displayName,
        String status
) {
}
