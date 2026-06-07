package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Email verification response DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailVerificationResponse(
        String id,
        String email,
        String status
) {
}
