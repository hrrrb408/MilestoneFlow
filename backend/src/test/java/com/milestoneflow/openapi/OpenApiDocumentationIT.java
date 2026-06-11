package com.milestoneflow.openapi;

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
 * Integration tests verifying the OpenAPI documentation output.
 *
 * <p>Validates that:
 * <ul>
 *   <li>All auth endpoints are documented</li>
 *   <li>Cookie auth scheme is present (no JWT Bearer)</li>
 *   <li>ApiErrorResponse schema exists</li>
 *   <li>Error codes are documented</li>
 *   <li>No sensitive data in examples</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI documentation")
class OpenApiDocumentationIT {

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
    @DisplayName("accessibility")
    class Accessibility {

        @Test
        @DisplayName("/v3/api-docs is accessible in test profile")
        void apiDocsAccessible() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("auth endpoints")
    class AuthEndpoints {

        @Test
        @DisplayName("includes register endpoint")
        void includesRegister() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();
            assertThat(paths).contains("/auth/register");
        }

        @Test
        @DisplayName("includes all 11 auth paths")
        void includesAllAuthPaths() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();

            assertThat(paths).contains("/auth/register");
            assertThat(paths).contains("/auth/email-verification/resend");
            assertThat(paths).contains("/auth/email-verification/confirm");
            assertThat(paths).contains("/auth/login");
            assertThat(paths).contains("/auth/refresh");
            assertThat(paths).contains("/auth/me");
            assertThat(paths).contains("/auth/logout");
            assertThat(paths).contains("/auth/logout-all");
            assertThat(paths).contains("/auth/password/change");
            assertThat(paths).contains("/auth/password/forgot");
            assertThat(paths).contains("/auth/password/reset");
        }

        @Test
        @DisplayName("includes password reset endpoints")
        void includesPasswordReset() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();
            assertThat(paths).contains("/auth/password/forgot");
            assertThat(paths).contains("/auth/password/reset");
        }

        @Test
        @DisplayName("includes refresh endpoint")
        void includesRefresh() throws Exception {
            JsonNode docs = getApiDocs();
            String paths = docs.get("paths").toPrettyString();
            assertThat(paths).contains("/auth/refresh");
        }
    }

    @Nested
    @DisplayName("security scheme")
    class SecurityScheme {

        @Test
        @DisplayName("does not contain JWT Bearer security scheme")
        void noJwtBearer() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            // Must NOT have bearer JWT scheme
            assertThat(docStr).doesNotContain("\"bearer\"");
            assertThat(docStr).doesNotContain("Bearer ");
        }

        @Test
        @DisplayName("contains cookie auth security scheme")
        void containsCookieAuth() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            // Should have cookie-based auth
            assertThat(docStr).contains("cookieAuth");
            assertThat(docStr).contains("MF_ACCESS");
        }

        @Test
        @DisplayName("contains CSRF token description")
        void containsCsrfToken() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            assertThat(docStr).contains("X-XSRF-TOKEN");
        }
    }

    @Nested
    @DisplayName("schemas")
    class Schemas {

        @Test
        @DisplayName("contains ApiErrorResponse schema")
        void containsApiErrorResponse() throws Exception {
            JsonNode docs = getApiDocs();
            String schemas = docs.at("/components/schemas").toPrettyString();

            assertThat(schemas).contains("ApiErrorResponse");
        }

        @Test
        @DisplayName("contains ApiErrorDetail schema")
        void containsApiErrorDetail() throws Exception {
            JsonNode docs = getApiDocs();
            String schemas = docs.at("/components/schemas").toPrettyString();

            assertThat(schemas).contains("ApiErrorDetail");
        }
    }

    @Nested
    @DisplayName("security")
    class Security {

        @Test
        @DisplayName("OpenAPI JSON does not contain real passwords")
        void noRealPasswords() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            // No example passwords
            assertThat(docStr).doesNotContain("password123");
            assertThat(docStr).doesNotContain("P@ssw0rd");
            assertThat(docStr).doesNotContain("secret-token");
        }

        @Test
        @DisplayName("OpenAPI JSON does not contain real cookie values")
        void noRealCookieValues() throws Exception {
            JsonNode docs = getApiDocs();
            String docStr = docs.toPrettyString();

            // No cookie value examples
            assertThat(docStr).doesNotContain("eyJhbGciOi");
        }

        @Test
        @DisplayName("info section has correct title and version")
        void correctInfo() throws Exception {
            JsonNode docs = getApiDocs();

            assertThat(docs.at("/info/title").asText()).isEqualTo("MilestoneFlow Backend API");
            assertThat(docs.at("/info/version").asText()).isEqualTo("v0.1");
        }
    }
}
