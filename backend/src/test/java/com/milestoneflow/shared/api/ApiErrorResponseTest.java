package com.milestoneflow.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldBuildMinimalError() {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 7, 10, 0, 0, 0, ZoneOffset.UTC);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(500)
                .code("INTERNAL_ERROR")
                .message("Something went wrong")
                .requestId("req-001")
                .path("/api/v1/test")
                .build();

        assertThat(error.status()).isEqualTo(500);
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).isEqualTo("Something went wrong");
        assertThat(error.requestId()).isEqualTo("req-001");
        assertThat(error.path()).isEqualTo("/api/v1/test");
        assertThat(error.fieldErrors()).isEmpty();
        assertThat(error.details()).isEmpty();
        assertThat(error.timestamp()).isEqualTo(fixedTimestamp);
    }

    @Test
    void shouldBuildErrorWithFieldErrors() {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 7, 10, 1, 0, 0, ZoneOffset.UTC);
        var fieldErrors = List.of(
                new ApiErrorDetail("name", "NotBlank", "must not be blank"),
                new ApiErrorDetail("email", "Email", "must be a valid email")
        );

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(422)
                .code("VALIDATION_FAILED")
                .message("Validation failed")
                .requestId("req-002")
                .path("/api/v1/test")
                .fieldErrors(fieldErrors)
                .build();

        assertThat(error.fieldErrors()).hasSize(2);
        assertThat(error.fieldErrors().get(0).field()).isEqualTo("name");
        assertThat(error.fieldErrors().get(1).code()).isEqualTo("Email");
    }

    @Test
    void shouldBuildErrorWithDetails() {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 7, 10, 2, 0, 0, ZoneOffset.UTC);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(409)
                .code("PROJECT_ARCHIVED")
                .message("Project is archived")
                .requestId("req-003")
                .path("/api/v1/projects/123")
                .details(Map.of("resourceId", "abc-123"))
                .build();

        assertThat(error.details()).containsEntry("resourceId", "abc-123");
    }

    @Test
    void shouldSerializeToJsonWithAllFields() throws Exception {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 6, 12, 30, 0, 0, ZoneOffset.UTC);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(422)
                .code("VALIDATION_FAILED")
                .message("Validation failed")
                .requestId("req-004")
                .path("/api/v1/test")
                .fieldErrors(List.of(new ApiErrorDetail("amount", "POSITIVE", "must be positive")))
                .build();

        String json = objectMapper.writeValueAsString(error);

        assertThat(json).contains("\"status\":422");
        assertThat(json).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(json).contains("\"requestId\":\"req-004\"");
        assertThat(json).contains("\"path\":\"/api/v1/test\"");
        assertThat(json).contains("\"fieldErrors\"");
        assertThat(json).contains("\"field\":\"amount\"");
    }

    @Test
    void shouldOmitEmptyFieldErrorsAndDetails() throws Exception {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 7, 10, 4, 0, 0, ZoneOffset.UTC);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(500)
                .code("INTERNAL_ERROR")
                .message("Error")
                .requestId("req-005")
                .path("/api/v1/test")
                .build();

        String json = objectMapper.writeValueAsString(error);

        assertThat(json).doesNotContain("fieldErrors");
        assertThat(json).doesNotContain("details");
    }

    @Test
    void shouldHandleNullFieldErrorsGracefully() {
        OffsetDateTime fixedTimestamp = OffsetDateTime.of(2026, 6, 7, 10, 5, 0, 0, ZoneOffset.UTC);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(fixedTimestamp)
                .status(400)
                .code("INVALID_REQUEST")
                .message("Bad request")
                .requestId("req-006")
                .path("/api/v1/test")
                .fieldErrors(null)
                .details(null)
                .build();

        assertThat(error.fieldErrors()).isEmpty();
        assertThat(error.details()).isEmpty();
    }
}
