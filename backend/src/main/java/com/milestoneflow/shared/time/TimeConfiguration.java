package com.milestoneflow.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide time configuration.
 *
 * <p>Provides a single {@link Clock} bean defaulting to UTC.
 * All application code should inject this clock and use
 * {@code Instant.now(clock)} instead of {@code Instant.now()}.
 *
 * <p>Tests can override the bean with {@link Clock#fixed} to assert on
 * exact timestamps without relying on system time.
 *
 * <p>Workspace business dates (IANA timezone interpretation) are a separate
 * concern and must not influence this global clock.
 */
@Configuration
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
