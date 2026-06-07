package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Registration response DTO.
 *
 * <p>Contains only safe, non-sensitive data.
 * No password, token, or internal fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationResponse(
        String id,
        String email,
        String status
) {
}
