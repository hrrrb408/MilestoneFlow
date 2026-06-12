package com.milestoneflow.task.integration;

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
 * Integration tests verifying task endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Task Documentation IT")
class OpenApiTaskDocumentationIT {

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
    @DisplayName("task endpoints")
    class TaskEndpoints {

        @Test
        @DisplayName("includes POST task endpoint")
        void includesCreateTask() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            assertThat(docs.get("paths").has(taskPath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(taskPath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("includes GET task list endpoint")
        void includesListTasks() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            JsonNode pathNode = docs.at("/paths").get(taskPath);
            assertThat(pathNode).isNotNull();
            assertThat(pathNode.has("get")).isTrue();
        }

        @Test
        @DisplayName("GET task list endpoint has status query parameter")
        void listEndpointHasStatusParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            JsonNode getList = docs.at("/paths").get(taskPath).get("get");
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
        @DisplayName("GET task list endpoint has priority query parameter")
        void listEndpointHasPriorityParameter() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            JsonNode getList = docs.at("/paths").get(taskPath).get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();

            boolean hasPriority = false;
            for (JsonNode param : parameters) {
                String name = param.get("name").asText();
                if ("priority".equals(name)) hasPriority = true;
            }
            assertThat(hasPriority)
                    .as("List endpoint should have priority query parameter")
                    .isTrue();
        }

        @Test
        @DisplayName("includes GET task detail endpoint")
        void includesGetTask() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}";
            assertThat(docs.get("paths").has(detailPath)).isTrue();
        }

        @Test
        @DisplayName("includes PATCH task endpoint")
        void includesUpdateTask() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}";
            JsonNode pathNode = docs.at("/paths").get(detailPath);
            assertThat(pathNode).isNotNull();
            assertThat(pathNode.has("patch")).isTrue();
        }

        @Test
        @DisplayName("task endpoints use cookieAuth security scheme")
        void taskEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";

            JsonNode taskGet = docs.at("/paths").get(taskPath).get("get");
            JsonNode security = taskGet.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("task endpoints do not contain JWT Bearer")
        void taskEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("OpenAPI schemas include TaskResponse")
        void includesTaskResponseSchema() throws Exception {
            JsonNode docs = getApiDocs();
            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("TaskResponse")).isTrue();
            assertThat(schemas.has("TaskListResponse")).isTrue();
        }

        @Test
        @DisplayName("task POST endpoint has security requirement")
        void createEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            JsonNode postNode = docs.at("/paths").get(taskPath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("task PATCH endpoint has security requirement")
        void updateEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String detailPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}";
            JsonNode patchNode = docs.at("/paths").get(detailPath).get("patch");
            JsonNode security = patchNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }
    }

    @Nested
    @DisplayName("task completion endpoints")
    class TaskCompletionEndpoints {

        @Test
        @DisplayName("includes POST complete endpoint")
        void includesCompleteEndpoint() throws Exception {
            JsonNode docs = getApiDocs();
            String completePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/complete";
            assertThat(docs.get("paths").has(completePath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(completePath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("includes POST reopen endpoint")
        void includesReopenEndpoint() throws Exception {
            JsonNode docs = getApiDocs();
            String reopenPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/reopen";
            assertThat(docs.get("paths").has(reopenPath)).isTrue();

            JsonNode pathNode = docs.at("/paths").get(reopenPath);
            assertThat(pathNode.has("post")).isTrue();
        }

        @Test
        @DisplayName("complete endpoint has security requirement")
        void completeEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String completePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/complete";
            JsonNode postNode = docs.at("/paths").get(completePath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("reopen endpoint has security requirement")
        void reopenEndpointHasSecurity() throws Exception {
            JsonNode docs = getApiDocs();
            String reopenPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/reopen";
            JsonNode postNode = docs.at("/paths").get(reopenPath).get("post");
            JsonNode security = postNode.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("complete endpoint does not contain JWT Bearer")
        void completeEndpointNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String completePath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/complete";
            JsonNode postNode = docs.at("/paths").get(completePath).get("post");
            String postStr = postNode.toPrettyString();
            assertThat(postStr).doesNotContain("\"bearer\"");
        }
    }
}
