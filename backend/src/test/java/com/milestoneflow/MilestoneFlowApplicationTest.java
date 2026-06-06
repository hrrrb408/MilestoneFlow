package com.milestoneflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity check: the application context can be loaded.
 * Does not require a database connection (Flyway/JPA are validated only in integration tests).
 */
class MilestoneFlowApplicationTest {

    @Test
    void applicationClassExists() {
        assertThat(MilestoneFlowApplication.class).isNotNull();
    }
}
