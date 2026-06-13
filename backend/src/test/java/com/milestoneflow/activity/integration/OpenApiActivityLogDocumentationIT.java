package com.milestoneflow.activity.integration;

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

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying activity log endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Activity Log Documentation IT")
class OpenApiActivityLogDocumentationIT {

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
    @DisplayName("activity log endpoints")
    class ActivityLogEndpoints {

        @Test
        @DisplayName("includes GET workspace activities endpoint")
        void includesWorkspaceActivities() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET project activities endpoint")
        void includesProjectActivities() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/activities";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET milestone activities endpoint")
        void includesMilestoneActivities() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/activities";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET task activities endpoint")
        void includesTaskActivities() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/activities";
            assertThat(docs.get("paths").has(path)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(path);
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("workspace activities endpoint has limit query parameter")
        void workspaceEndpointHasLimitParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            JsonNode getList = docs.at("/paths").get(path).get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();
            boolean hasLimit = false;
            for (JsonNode param : parameters) {
                if ("limit".equals(param.get("name").asText())) hasLimit = true;
            }
            assertThat(hasLimit).isTrue();
        }

        @Test
        @DisplayName("workspace activities endpoint has eventType query parameter")
        void workspaceEndpointHasEventTypeParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            JsonNode getList = docs.at("/paths").get(path).get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();
            boolean hasEventType = false;
            for (JsonNode param : parameters) {
                if ("eventType".equals(param.get("name").asText())) hasEventType = true;
            }
            assertThat(hasEventType).isTrue();
        }

        @Test
        @DisplayName("workspace activities endpoint has targetType query parameter")
        void workspaceEndpointHasTargetTypeParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            JsonNode getList = docs.at("/paths").get(path).get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();
            boolean hasTargetType = false;
            for (JsonNode param : parameters) {
                if ("targetType".equals(param.get("name").asText())) hasTargetType = true;
            }
            assertThat(hasTargetType).isTrue();
        }

        @Test
        @DisplayName("activity endpoints use cookieAuth security scheme")
        void activityEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";

            JsonNode getActivity = docs.at("/paths").get(path).get("get");
            JsonNode security = getActivity.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("activity endpoints do not contain JWT Bearer")
        void activityEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            JsonNode getNode = docs.at("/paths").get(path).get("get");
            String getStr = getNode.toPrettyString();
            assertThat(getStr).doesNotContain("\"bearer\"");
            assertThat(getStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("GET endpoints do not declare csrfToken")
        void getEndpointsNoCsrf() throws Exception {
            JsonNode docs = getApiDocs();
            String path = "/workspaces/{workspaceId}/activities";
            JsonNode getNode = docs.at("/paths").get(path).get("get");
            String getStr = getNode.toPrettyString();
            assertThat(getStr).doesNotContain("csrfToken");
        }

        @Test
        @DisplayName("OpenAPI schemas include ActivityLogResponse")
        void includesActivityLogResponseSchema() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("ActivityLogResponse")).isTrue();
            assertThat(schemas.has("ActivityLogListResponse")).isTrue();
        }

        @Test
        @DisplayName("ActivityLogResponse schema has expected fields")
        void activityLogResponseHasExpectedFields() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schema = docs.at("/components/schemas/ActivityLogResponse");
            JsonNode properties = schema.get("properties");

            assertThat(properties).isNotNull();
            Set<String> fieldNames = new HashSet<>();
            properties.fieldNames().forEachRemaining(fieldNames::add);
            assertThat(fieldNames).containsExactlyInAnyOrder(
                    "id", "workspaceId", "actorId", "actorType",
                    "eventType", "targetType", "targetId",
                    "summary", "metadata", "createdAt"
            );
        }

        @Test
        @DisplayName("ActivityLogResponse does not expose internal fields")
        void activityLogResponseNoInternalFields() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schema = docs.at("/components/schemas/ActivityLogResponse");
            JsonNode properties = schema.get("properties");

            assertThat(properties).isNotNull();
            Set<String> fieldNames = new HashSet<>();
            properties.fieldNames().forEachRemaining(fieldNames::add);
            // Should NOT expose internal database fields
            assertThat(fieldNames).doesNotContain("requestId", "source", "action");
        }

        @Test
        @DisplayName("ActivityLogListResponse schema has items and nextCursor")
        void activityLogListResponseHasFields() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schema = docs.at("/components/schemas/ActivityLogListResponse");
            JsonNode properties = schema.get("properties");

            assertThat(properties).isNotNull();
            assertThat(properties.has("items")).isTrue();
            assertThat(properties.has("nextCursor")).isTrue();
        }
    }
}
