package com.milestoneflow.identity.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for successful password change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordChangeResponse(boolean passwordChanged) {
}
