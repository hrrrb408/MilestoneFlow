package com.milestoneflow.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigurationTest {

    private final TimeConfiguration configuration = new TimeConfiguration();

    @Test
    void shouldProvideClockBean() {
        Clock clock = configuration.clock();

        assertThat(clock).isNotNull();
    }

    @Test
    void shouldDefaultToUtc() {
        Clock clock = configuration.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void shouldProduceCurrentInstant() {
        Clock clock = configuration.clock();

        Instant before = Instant.now(clock);
        Instant actual = Instant.now(clock);
        Instant after = Instant.now(clock);

        assertThat(actual)
                .isBetween(before, after);
    }

    @Test
    void shouldSupportFixedClockForTesting() {
        Instant fixedInstant = Instant.parse("2026-06-07T12:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        assertThat(Instant.now(fixedClock)).isEqualTo(fixedInstant);
        assertThat(Instant.now(fixedClock)).isEqualTo(fixedInstant);
    }

    @Test
    void shouldProduceUtcOffsetDateTime() {
        Clock clock = configuration.clock();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(clock);

        assertThat(now.getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
