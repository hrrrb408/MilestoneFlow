package com.milestoneflow.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unified error response matching architecture spec §10.
 *
 * <p>Production responses must never include SQL, class names, stack traces,
 * tokens, or internal storage paths.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String requestId,
        String path,
        @JsonProperty("fieldErrors") List<ApiErrorDetail> fieldErrors,
        @JsonProperty("details") Map<String, Object> details
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private OffsetDateTime timestamp;
        private int status;
        private String code;
        private String message;
        private String requestId;
        private String path;
        private List<ApiErrorDetail> fieldErrors = Collections.emptyList();
        private Map<String, Object> details = Collections.emptyMap();

        private Builder() {}

        public Builder timestamp(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder fieldErrors(List<ApiErrorDetail> fieldErrors) {
            this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyList();
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details != null ? details : Collections.emptyMap();
            return this;
        }

        public ApiErrorResponse build() {
            return new ApiErrorResponse(
                    timestamp, status, code, message, requestId, path, fieldErrors, details
            );
        }
    }
}
