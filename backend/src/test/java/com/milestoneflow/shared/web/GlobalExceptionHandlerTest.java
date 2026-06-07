package com.milestoneflow.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestoneflow.shared.api.ApiErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link GlobalExceptionHandler} using a lightweight {@code @WebMvcTest}
 * slice with an inner test controller registered via {@code @Configuration}.
 */
@WebMvcTest
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GlobalExceptionHandlerTest.TestConfig.class})
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Configuration
    static class TestConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .securityMatcher("/_test/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @RestController
    @RequestMapping("/_test")
    static class TestController {

        @GetMapping("/runtime")
        public String throwRuntime() {
            throw new RuntimeException("test runtime exception");
        }

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }
    }

    record SampleRequest(
            @NotBlank String name,
            @Email String email
    ) {}

    @Test
    void shouldReturn500ForUnhandledException() throws Exception {
        mockMvc.perform(get("/_test/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> {
                    ApiErrorResponse error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ApiErrorResponse.class
                    );
                    assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
                    assertThat(error.status()).isEqualTo(500);
                    assertThat(error.requestId()).isNotNull();
                    assertThat(error.path()).isNotNull();
                });
    }

    @Test
    void shouldReturn400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/_test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    ApiErrorResponse error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ApiErrorResponse.class
                    );
                    assertThat(error.code()).isEqualTo("INVALID_REQUEST");
                    assertThat(error.status()).isEqualTo(400);
                });
    }

    @Test
    void shouldReturn422ForValidationErrors() throws Exception {
        mockMvc.perform(post("/_test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> {
                    ApiErrorResponse error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ApiErrorResponse.class
                    );
                    assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(error.status()).isEqualTo(422);
                    assertThat(error.fieldErrors()).isNotEmpty();
                });
    }

    @Test
    void shouldIncludeRequestIdInResponseHeader() throws Exception {
        mockMvc.perform(get("/_test/runtime"))
                .andExpect(result -> {
                    String requestId = result.getResponse().getHeader("X-Request-Id");
                    assertThat(requestId).isNotNull();
                    java.util.UUID.fromString(requestId);
                });
    }

    @Test
    void shouldUseClientProvidedRequestId() throws Exception {
        String clientRequestId = java.util.UUID.randomUUID().toString();

        mockMvc.perform(get("/_test/runtime")
                        .header("X-Request-Id", clientRequestId))
                .andExpect(result -> {
                    String requestId = result.getResponse().getHeader("X-Request-Id");
                    assertThat(requestId).isEqualTo(clientRequestId);

                    ApiErrorResponse error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ApiErrorResponse.class
                    );
                    assertThat(error.requestId()).isEqualTo(clientRequestId);
                });
    }
}
