package com.milestoneflow.milestone.integration;

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
 * Integration tests verifying milestone endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Milestone Documentation IT")
class OpenApiMilestoneDocumentationIT {

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
    @DisplayName("milestone endpoints")
    class MilestoneEndpoints {

        @Test
        @DisplayName("includes POST milestone endpoint")
        void includesCreateMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String milestonePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones";
            assertThat(docs.get("paths").has(milestonePath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(milestonePath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("includes GET milestone list endpoint")
        void includesListMilestones() throws Exception {
            JsonNode docs = getApiDocs();
            String milestonePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones";
            JsonNode pathNode = docs.at("/paths").get(milestonePath);
            assertThat(pathNode).isNotNull();
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("GET milestone list endpoint has status query parameter")
        void listEndpointHasStatusParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String milestonePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones";
            JsonNode getList = docs.at("/paths").get(milestonePath).get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();

            boolean hasStatus = false;
            for (JsonNode param : parameters) {
                String name = param.get("name").asText();
                if ("status".equals(name)) hasStatus = true;
            }
            assertThat(hasStatus)
                    .as("List endpoint should have status query parameter")
                    .isTrue();
        }

        @Test
        @DisplayName("includes GET milestone detail endpoint")
        void includesGetMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}";
            assertThat(docs.get("paths").has(detailPath)).isTrue();
        }

        @Test
        @DisplayName("includes PATCH milestone endpoint")
        void includesUpdateMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}";
            JsonNode pathNode = docs.at("/paths").get(detailPath);
            assertThat(pathNode).isNotNull();
            assertThat(pathNode.has("patch")).isTrue();
        }

        @Test
        @DisplayName("milestone endpoints use cookieAuth security scheme")
        void milestoneEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();
            String milestonePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones";

            JsonNode milestoneGet = docs.at("/paths").get(milestonePath).get("get");
            JsonNode security = milestoneGet.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("milestone endpoints do not contain JWT Bearer")
        void milestoneEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("OpenAPI schemas include MilestoneResponse")
        void includesMilestoneResponseSchema() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("MilestoneResponse")).isTrue();
            assertThat(schemas.has("MilestoneListResponse")).isTrue();
        }

        @Test
        @DisplayName("milestone POST endpoint has security requirement")
        void createEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String milestonePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones";
            JsonNode postNode = docs.at("/paths").get(milestonePath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("milestone PATCH endpoint has security requirement")
        void updateEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}";
            JsonNode patchNode = docs.at("/paths").get(detailPath).get("patch");
            JsonNode security = patchNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("includes POST milestone complete endpoint")
        void includesCompleteMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String completePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/complete";
            assertThat(docs.get("paths").has(completePath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(completePath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("includes POST milestone reopen endpoint")
        void includesReopenMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String reopenPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/reopen";
            assertThat(docs.get("paths").has(reopenPath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(reopenPath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("complete endpoint has security requirement")
        void completeEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String completePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/complete";
            JsonNode postNode = docs.at("/paths").get(completePath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("reopen endpoint has security requirement")
        void reopenEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String reopenPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/reopen";
            JsonNode postNode = docs.at("/paths").get(reopenPath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }
    }
}
