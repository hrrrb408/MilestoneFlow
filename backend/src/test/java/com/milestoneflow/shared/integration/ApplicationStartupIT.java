package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the application starts successfully and the Actuator health
 * endpoint is reachable with a PostgreSQL database.
 */
class ApplicationStartupIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldStartApplicationAndExposeHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/health", String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\"");
    }
}
