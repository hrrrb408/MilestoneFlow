package com.milestoneflow.project.integration;

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
 * Integration tests verifying project endpoints in OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Project Documentation IT")
class OpenApiProjectDocumentationIT {

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
    @DisplayName("project endpoints")
    class ProjectEndpoints {

        @Test
        @DisplayName("includes POST /workspaces/{workspaceId}/projects endpoint")
        void includesCreateProject() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();

            assertThat(paths).contains("/workspaces/{workspaceId}/projects");
        }

        @Test
        @DisplayName("includes GET /workspaces/{workspaceId}/projects endpoint")
        void includesListProjects() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode projectsPath = docs.at("/paths").get("/workspaces/{workspaceId}/projects");
            assertThat(projectsPath).isNotNull();
            assertThat(projectsPath.has("get")).isTrue();
        }

        @Test
        @DisplayName("GET /projects list endpoint has includeArchived and status parameters")
        void listEndpointHasFilterParameters() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode getList = docs.at("/paths")
                    .get("/workspaces/{workspaceId}/projects").get("get");
            JsonNode parameters = getList.get("parameters");

            assertThat(parameters).isNotNull();

            // Check includeArchived parameter
            boolean hasIncludeArchived = false;
            boolean hasStatus = false;
            for (JsonNode param : parameters) {
                String name = param.get("name").asText();
                if ("includeArchived".equals(name)) hasIncludeArchived = true;
                if ("status".equals(name)) hasStatus = true;
            }
            assertThat(hasIncludeArchived)
                    .as("List endpoint should have includeArchived query parameter")
                    .isTrue();
            assertThat(hasStatus)
                    .as("List endpoint should have status query parameter")
                    .isTrue();
        }

        @Test
        @DisplayName("includes GET /workspaces/{workspaceId}/projects/{projectId} endpoint")
        void includesGetProject() throws Exception {
            JsonNode docs = getApiDocs();

            assertThat(docs.get("paths").has("/workspaces/{workspaceId}/projects/{projectId}")).isTrue();
        }

        @Test
        @DisplayName("includes PATCH /workspaces/{workspaceId}/projects/{projectId} endpoint")
        void includesUpdateProject() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode projectDetail = docs.at("/paths").get("/workspaces/{workspaceId}/projects/{projectId}");
            assertThat(projectDetail).isNotNull();
            assertThat(projectDetail.has("patch")).isTrue();
        }

        @Test
        @DisplayName("includes POST /workspaces/{workspaceId}/projects/{projectId}/archive endpoint")
        void includesArchiveProject() throws Exception {
            JsonNode docs = getApiDocs();

            String archivePath = "/workspaces/{workspaceId}/projects/{projectId}/archive";
            assertThat(docs.get("paths").has(archivePath)).isTrue();

            JsonNode archive = docs.at("/paths").get(archivePath);
            assertThat(archive.has("post")).isTrue();
        }

        @Test
        @DisplayName("includes POST /workspaces/{workspaceId}/projects/{projectId}/restore endpoint")
        void includesRestoreProject() throws Exception {
            JsonNode docs = getApiDocs();

            String restorePath = "/workspaces/{workspaceId}/projects/{projectId}/restore";
            assertThat(docs.get("paths").has(restorePath)).isTrue();

            JsonNode restore = docs.at("/paths").get(restorePath);
            assertThat(restore.has("post")).isTrue();
        }

        @Test
        @DisplayName("project endpoints use cookieAuth security scheme")
        void projectEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode projectsGet = docs.at("/paths")
                    .get("/workspaces/{workspaceId}/projects").get("get");
            JsonNode security = projectsGet.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("archive endpoint uses cookieAuth security scheme")
        void archiveEndpointUsesCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();

            String archivePath = "/workspaces/{workspaceId}/projects/{projectId}/archive";
            JsonNode archivePost = docs.at("/paths").get(archivePath).get("post");
            JsonNode security = archivePost.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("restore endpoint uses cookieAuth security scheme")
        void restoreEndpointUsesCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();

            String restorePath = "/workspaces/{workspaceId}/projects/{projectId}/restore";
            JsonNode restorePost = docs.at("/paths").get(restorePath).get("post");
            JsonNode security = restorePost.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("project endpoints do not contain JWT Bearer")
        void projectEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("OpenAPI schemas include ProjectResponse")
        void includesProjectResponseSchema() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("ProjectResponse")).isTrue();
            assertThat(schemas.has("ProjectListResponse")).isTrue();
        }

        @Test
        @DisplayName("task endpoints are exposed under milestone path")
        void taskEndpointsUnderMilestone() throws Exception {
            JsonNode docs = getApiDocs();
            String taskPath = "/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks";
            assertThat(docs.get("paths").has(taskPath)).isTrue();
        }
    }
}
