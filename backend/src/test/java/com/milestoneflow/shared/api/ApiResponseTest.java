package com.milestoneflow.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateResponseWithDataAndRequestId() {
        ApiResponse<String> response = ApiResponse.of("hello", "req-001");

        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.meta().requestId()).isEqualTo("req-001");
    }

    @Test
    void shouldCreateResponseWithNullData() {
        ApiResponse<Void> response = new ApiResponse<>(null, "req-002");

        assertThat(response.data()).isNull();
        assertThat(response.meta().requestId()).isEqualTo("req-002");
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        ApiResponse<String> response = ApiResponse.of("payload", "req-003");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).contains("\"meta\"");
        assertThat(json).contains("\"requestId\":\"req-003\"");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {"data":42,"meta":{"requestId":"req-004"}}
                """;
        ApiResponse<?> response = objectMapper.readValue(json, ApiResponse.class);

        assertThat(response.meta().requestId()).isEqualTo("req-004");
    }
}
