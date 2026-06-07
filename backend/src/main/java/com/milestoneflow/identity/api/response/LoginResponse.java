package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Login response DTO.
 *
 * <p>Contains only safe, non-sensitive data.
 * No tokens, no hashes, no session internals.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String userId,
        String email,
        String displayName,
        String status
) {
}
