package com.milestoneflow.progress.integration;

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
 * Integration tests verifying progress endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Progress Documentation IT")
class OpenApiProgressDocumentationIT {

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
    @DisplayName("progress endpoints")
    class ProgressEndpoints {

        @Test
        @DisplayName("includes GET project progress endpoint")
        void includesProjectProgress() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/progress";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET milestone progress endpoint")
        void includesMilestoneProgress() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/progress";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET milestone progress list endpoint")
        void includesMilestoneProgressList() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/milestones/progress";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("progress endpoints use cookieAuth security scheme")
        void progressEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/progress";

            JsonNode getNode = docs.at("/paths").get(path).get("get");
            JsonNode security = getNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("progress endpoints do not contain JWT Bearer")
        void progressEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("GET endpoints do not declare csrfToken")
        void getEndpointsDoNotRequireCsrf() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/progress";
            JsonNode getNode = docs.at("/paths").get(path).get("get");
            String getStr = getNode.toPrettyString();

            // GET endpoints should not have csrfToken in security requirements
            assertThat(getStr).doesNotContain("csrfToken");
        }
    }

    @Nested
    @DisplayName("progress schemas")
    class ProgressSchemas {

        @Test
        @DisplayName("OpenAPI schemas include ProjectProgressResponse")
        void includesProjectProgressResponse() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("ProjectProgressResponse")).isTrue();
        }

        @Test
        @DisplayName("OpenAPI schemas include MilestoneProgressResponse")
        void includesMilestoneProgressResponse() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("MilestoneProgressResponse")).isTrue();
        }

        @Test
        @DisplayName("OpenAPI schemas include MilestoneProgressListResponse")
        void includesMilestoneProgressListResponse() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("MilestoneProgressListResponse")).isTrue();
        }

        @Test
        @DisplayName("ProjectProgressResponse contains completionRate field")
        void projectResponseHasCompletionRate() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode props = docs.at("/components/schemas/ProjectProgressResponse/properties");
            assertThat(props.has("completionRate")).isTrue();
            assertThat(props.has("totalTasks")).isTrue();
            assertThat(props.has("completedTasks")).isTrue();
            assertThat(props.has("openTasks")).isTrue();
            assertThat(props.has("totalMilestones")).isTrue();
        }

        @Test
        @DisplayName("MilestoneProgressResponse contains milestoneId and completionRate")
        void milestoneResponseHasMilestoneId() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode props = docs.at("/components/schemas/MilestoneProgressResponse/properties");
            assertThat(props.has("milestoneId")).isTrue();
            assertThat(props.has("completionRate")).isTrue();
            assertThat(props.has("milestoneStatus")).isTrue();
            assertThat(props.has("totalTasks")).isTrue();
        }

        @Test
        @DisplayName("ProjectProgressResponse exposes exactly the contracted field set")
        void projectResponseFieldSetIsComplete() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode props = docs.at("/components/schemas/ProjectProgressResponse/properties");

            assertThat(schemaFieldNames(props)).containsExactlyInAnyOrder(
                    "workspaceId", "projectId",
                    "totalTasks", "completedTasks", "openTasks", "completionRate",
                    "totalMilestones", "completedMilestones", "openMilestones");
        }

        @Test
        @DisplayName("MilestoneProgressResponse exposes exactly the contracted field set")
        void milestoneResponseFieldSetIsComplete() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode props = docs.at("/components/schemas/MilestoneProgressResponse/properties");

            assertThat(schemaFieldNames(props)).containsExactlyInAnyOrder(
                    "workspaceId", "projectId", "milestoneId",
                    "milestoneTitle", "milestoneStatus",
                    "totalTasks", "completedTasks", "openTasks", "completionRate");
        }

        @Test
        @DisplayName("MilestoneProgressListResponse exposes only the items field")
        void listResponseFieldSetIsComplete() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode props = docs.at("/components/schemas/MilestoneProgressListResponse/properties");

            assertThat(schemaFieldNames(props)).containsExactly("items");
        }

        @Test
        @DisplayName("progress responses do not leak internal/audit fields")
        void progressResponsesDoNotLeakInternalFields() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode projectProps = docs.at("/components/schemas/ProjectProgressResponse/properties");
            JsonNode milestoneProps = docs.at("/components/schemas/MilestoneProgressResponse/properties");

            for (String forbidden : new String[]{"createdBy", "updatedBy", "completedBy",
                    "settings", "version", "createdAt", "updatedAt"}) {
                assertThat(projectProps.has(forbidden))
                        .as("ProjectProgressResponse must not expose %s", forbidden).isFalse();
                assertThat(milestoneProps.has(forbidden))
                        .as("MilestoneProgressResponse must not expose %s", forbidden).isFalse();
            }
        }

        private java.util.Set<String> schemaFieldNames(JsonNode properties) {
            java.util.Set<String> names = new java.util.TreeSet<>();
            properties.fieldNames().forEachRemaining(names::add);
            return names;
        }
    }
}
