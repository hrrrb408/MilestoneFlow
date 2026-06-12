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

    @Nested
    @DisplayName("workspace member endpoints")
    class WorkspaceMemberEndpoints {

        @Test
        @DisplayName("includes GET /workspaces/{workspaceId}/members endpoint")
        void includesListMembers() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode membersPath = docs.at("/paths").get("/workspaces/{workspaceId}/members");
            assertThat(membersPath).isNotNull();
            assertThat(membersPath.has("get")).isTrue();
        }

        @Test
        @DisplayName("includes GET /workspaces/{workspaceId}/members/me endpoint")
        void includesCurrentMembership() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode mePath = docs.at("/paths").get("/workspaces/{workspaceId}/members/me");
            assertThat(mePath).isNotNull();
            assertThat(mePath.has("get")).isTrue();
        }

        @Test
        @DisplayName("member endpoints use cookieAuth security scheme")
        void memberEndpointsUseCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode membersGet = docs.at("/paths")
                    .get("/workspaces/{workspaceId}/members").get("get");
            JsonNode security = membersGet.get("security");
            assertThat(security).isNotNull();
            assertThat(security.toString()).contains("cookieAuth");

            JsonNode meGet = docs.at("/paths")
                    .get("/workspaces/{workspaceId}/members/me").get("get");
            assertThat(meGet.get("security").toString()).contains("cookieAuth");
        }

        @Test
        @DisplayName("OpenAPI schemas include WorkspaceMemberResponse")
        void includesMemberResponseSchema() throws Exception {
            JsonNode docs = getApiDocs();

            JsonNode schemas = docs.at("/components/schemas");
            assertThat(schemas.has("WorkspaceMemberResponse")).isTrue();
            assertThat(schemas.has("WorkspaceMembersResponse")).isTrue();
            assertThat(schemas.has("CurrentWorkspaceMembershipResponse")).isTrue();
        }

        @Test
        @DisplayName("member endpoints do not contain JWT Bearer")
        void memberEndpointsNoJwt() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("no invitation endpoints are exposed")
        void noInvitationEndpoints() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();

            // Invitations are reserved for a future task and must NOT be exposed.
            assertThat(paths).doesNotContain("/invitations");
            assertThat(paths).doesNotContain("/workspace-invitations");
        }
    }
}
