package com.milestoneflow.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponsePaginationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateSingleObjectResponse() {
        ApiResponse<String> response = ApiResponse.of("payload", "req-001");

        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.meta().requestId()).isEqualTo("req-001");
        assertThat(response.meta().page()).isNull();
        assertThat(response.meta().size()).isNull();
        assertThat(response.meta().totalElements()).isNull();
        assertThat(response.meta().totalPages()).isNull();
        assertThat(response.meta().hasNext()).isNull();
    }

    @Test
    void shouldCreateListResponse() {
        ApiResponse<List<String>> response = ApiResponse.of(
                List.of("a", "b", "c"), "req-002"
        );

        assertThat(response.data()).containsExactly("a", "b", "c");
        assertThat(response.meta().requestId()).isEqualTo("req-002");
        // No pagination fields for plain list
        assertThat(response.meta().page()).isNull();
    }

    @Test
    void shouldCreatePaginatedResponse() {
        List<String> items = List.of("item1", "item2");
        PageMeta pageMeta = PageMeta.of(0, 20, 100);
        ApiResponse<List<String>> response = ApiResponse.paged(items, "req-003", pageMeta);

        assertThat(response.data()).containsExactly("item1", "item2");
        assertThat(response.meta().requestId()).isEqualTo("req-003");
        assertThat(response.meta().page()).isEqualTo(0);
        assertThat(response.meta().size()).isEqualTo(20);
        assertThat(response.meta().totalElements()).isEqualTo(100);
        assertThat(response.meta().totalPages()).isEqualTo(5);
        assertThat(response.meta().hasNext()).isTrue();
    }

    @Test
    void shouldSerializeSingleObjectResponseToJson() throws Exception {
        ApiResponse<String> response = ApiResponse.of("payload", "req-004");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).contains("\"requestId\":\"req-004\"");
        assertThat(json).doesNotContain("page");
        assertThat(json).doesNotContain("totalElements");
        assertThat(json).doesNotContain("hasNext");
    }

    @Test
    void shouldSerializePaginatedResponseToJson() throws Exception {
        List<String> items = List.of("a", "b");
        PageMeta pageMeta = PageMeta.of(0, 20, 55);
        ApiResponse<List<String>> response = ApiResponse.paged(items, "req-005", pageMeta);
        String json = objectMapper.writeValueAsString(response);

        // Verify ADR-BE-009 flat structure
        assertThat(json).contains("\"data\":[\"a\",\"b\"]");
        assertThat(json).contains("\"requestId\":\"req-005\"");
        assertThat(json).contains("\"page\":0");
        assertThat(json).contains("\"size\":20");
        assertThat(json).contains("\"totalElements\":55");
        assertThat(json).contains("\"totalPages\":3");
        assertThat(json).contains("\"hasNext\":true");
    }

    @Test
    void shouldSerializeEmptyPaginatedResponse() throws Exception {
        PageMeta pageMeta = PageMeta.of(0, 20, 0);
        ApiResponse<List<String>> response = ApiResponse.paged(List.of(), "req-006", pageMeta);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"data\":[]");
        assertThat(json).contains("\"totalElements\":0");
        assertThat(json).contains("\"totalPages\":0");
        assertThat(json).contains("\"hasNext\":false");
    }

    @Test
    void shouldDeserializePaginatedResponse() throws Exception {
        String json = """
                {"data":["x"],"meta":{"requestId":"req-007","page":0,"size":10,"totalElements":1,"totalPages":1,"hasNext":false}}
                """;
        ApiResponse<?> response = objectMapper.readValue(json, ApiResponse.class);

        assertThat(response.meta().requestId()).isEqualTo("req-007");
        assertThat(response.meta().page()).isEqualTo(0);
        assertThat(response.meta().size()).isEqualTo(10);
        assertThat(response.meta().totalElements()).isEqualTo(1);
        assertThat(response.meta().totalPages()).isEqualTo(1);
        assertThat(response.meta().hasNext()).isFalse();
    }

    @Test
    void shouldDeserializeNonPaginatedResponse() throws Exception {
        String json = """
                {"data":42,"meta":{"requestId":"req-008"}}
                """;
        ApiResponse<?> response = objectMapper.readValue(json, ApiResponse.class);

        assertThat(response.meta().requestId()).isEqualTo("req-008");
        assertThat(response.meta().page()).isNull();
    }

    @Test
    void shouldNotMixPaginationFieldsIntoData() {
        // Ensure pagination fields are in meta, not data
        PageMeta pageMeta = PageMeta.of(1, 10, 25);
        ApiResponse<List<String>> response = ApiResponse.paged(
                List.of("item"), "req-009", pageMeta
        );

        // data is just the items list, not a map containing page info
        assertThat(response.data()).isInstanceOf(List.class);
        assertThat(response.data()).hasSize(1);
    }
}
