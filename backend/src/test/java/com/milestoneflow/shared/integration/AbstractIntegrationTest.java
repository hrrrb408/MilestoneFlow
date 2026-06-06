package com.milestoneflow.shared.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * <p>Uses Testcontainers to spin up a PostgreSQL 17 container. The container
 * lifecycle is managed by the {@code @Testcontainers} extension.
 *
 * <p>Subclasses should annotate with {@code @Test} as usual. The Spring context
 * is loaded once per test class by default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("milestoneflow_test");
}
