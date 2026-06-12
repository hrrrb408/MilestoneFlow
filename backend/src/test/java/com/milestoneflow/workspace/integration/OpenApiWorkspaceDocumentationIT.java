package com.milestoneflow.workspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying workspace endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Workspace Documentation IT")
class OpenApiWorkspaceDocumentationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode getApiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Nested
    @DisplayName("workspace endpoints")
    class WorkspaceEndpoints {

        @Test
        @DisplayName("includes POST /workspaces endpoint")
        void includesCreateWorkspace() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();

            assertThat(paths).contains("/workspaces");
        }

        @Test
        @DisplayName("includes GET /workspaces/current endpoint")
        void includesGetCurrent() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();

            assertThat(paths).contains("/workspaces/current");
        }

        @Test
        @DisplayName("includes GET /workspaces/{workspaceId} endpoint")
        void includesGetById() throws Exception {
            JsonNode docs = getApiDocs();
            assertThat(docs.get("paths").has("/workspaces/{workspaceId}")).isTrue();
        }

        @Test
        @DisplayName("includes PATCH /workspaces/{workspaceId} endpoint")
        void includesUpdate() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode workspaceDetail = docs.at("/paths").get("/workspaces/{workspaceId}");
            assertThat(workspaceDetail).isNotNull();
            assertThat(workspaceDetail.has("patch")).isTrue();
        }

        @Test
        @DisplayName("workspace endpoints do not contain JWT Bearer")
        void workspaceEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }
    }
}
