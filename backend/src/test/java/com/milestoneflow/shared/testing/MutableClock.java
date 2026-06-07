package com.milestoneflow.shared.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A test utility clock that starts at a fixed instant and can be advanced programmatically.
 *
 * <p>Thread-safe via {@link AtomicReference}. Designed for single-threaded test usage
 * where deterministic time control is needed (e.g., token expiry tests).
 *
 * <p>Does NOT enter production code — only exists in the test source root.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = new AtomicReference<>(instant);
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.instant.get(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    /**
     * Advances the clock by the given duration.
     *
     * @param duration the duration to advance (must not be null)
     */
    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    /**
     * Sets the clock to a specific instant.
     *
     * @param newInstant the new instant (must not be null)
     */
    public void setInstant(Instant newInstant) {
        instant.set(newInstant);
    }
}
