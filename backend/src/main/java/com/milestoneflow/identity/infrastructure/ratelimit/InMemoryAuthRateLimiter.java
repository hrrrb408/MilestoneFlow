package com.milestoneflow.identity.infrastructure.ratelimit;

import com.milestoneflow.identity.application.ratelimit.AuthRateLimitAction;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;
import com.milestoneflow.identity.application.port.out.AuthRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window rate limiter for authentication actions.
 *
 * <p>Uses a {@link ConcurrentHashMap} with composite keys (action + window + identifier).
 * Each window tracks a counter of attempts. When the window expires, a new window starts.
 *
 * <p>V0.1 limitations:
 * <ul>
 *   <li>Not distributed — single JVM only</li>
 *   <li>Counter resets on application restart</li>
 *   <li>Not suitable for multi-instance deployments</li>
 * </ul>
 *
 * <p>Thread safety: all state mutations use {@link ConcurrentHashMap} and
 * {@link AtomicLong} for safe concurrent access.
 */
@org.springframework.stereotype.Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuthRateLimiter.class);

    private final AuthRateLimitProperties properties;
    private final Clock clock;

    /**
     * Composite key: action name + window start epoch millis + identifier hash.
     */
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public InMemoryAuthRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision check(AuthRateLimitAction action, String key) {
        if (!properties.enabled()) {
            return RateLimitDecision.allowed(Long.MAX_VALUE);
        }

        AuthRateLimitProperties.Policy policy = properties.getPolicy(action.name());
        if (policy == null) {
            return RateLimitDecision.allowed(Long.MAX_VALUE);
        }

        String counterKey = buildKey(action, key, policy.window());
        long currentCount = counters.computeIfAbsent(counterKey, k -> new AtomicLong(0))
                .incrementAndGet();

        if (currentCount > policy.maxAttempts()) {
            Duration windowRemaining = getWindowRemaining(action, policy.window());
            return RateLimitDecision.rejected(windowRemaining.toSeconds());
        }

        return RateLimitDecision.allowed(policy.maxAttempts() - currentCount);
    }

    @Override
    public void reset(AuthRateLimitAction action, String key) {
        AuthRateLimitProperties.Policy policy = properties.getPolicy(action.name());
        if (policy == null) {
            return;
        }

        // Key format: ACTION:windowStart:key
        // Remove all entries for this action+key combination
        String actionPrefix = action.name() + ":";
        String keySuffix = ":" + key;
        counters.keySet().removeIf(k ->
                k.startsWith(actionPrefix) && k.endsWith(keySuffix));

        log.debug("Rate limit reset for action={} key={}", action, key);
    }

    @Override
    public void recordFailure(AuthRateLimitAction action, String key) {
        if (!properties.enabled()) {
            return;
        }

        AuthRateLimitProperties.Policy policy = properties.getPolicy(action.name());
        if (policy == null) {
            return;
        }

        String counterKey = buildKey(action, key, policy.window());
        counters.computeIfAbsent(counterKey, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    @Override
    public void cleanup() {
        Instant now = Instant.now(clock);
        int removed = 0;

        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            String[] parts = entry.getKey().split(":", 3);
            if (parts.length < 3) {
                continue;
            }

            String actionName = parts[0];
            AuthRateLimitProperties.Policy policy = properties.getPolicy(actionName);
            if (policy == null) {
                counters.remove(entry.getKey());
                removed++;
                continue;
            }

            try {
                long windowStart = Long.parseLong(parts[1]);
                Instant windowStartInstant = Instant.ofEpochMilli(windowStart);
                if (now.isAfter(windowStartInstant.plus(policy.window()))) {
                    counters.remove(entry.getKey());
                    removed++;
                }
            } catch (NumberFormatException e) {
                counters.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("Cleaned up {} expired rate limit windows", removed);
        }
    }

    private String buildKey(AuthRateLimitAction action, String key, Duration window) {
        long windowStart = computeWindowStart(window);
        return action.name() + ":" + windowStart + ":" + key;
    }

    private long computeWindowStart(Duration window) {
        long windowMillis = window.toMillis();
        long nowMillis = Instant.now(clock).toEpochMilli();
        return (nowMillis / windowMillis) * windowMillis;
    }

    private Duration getWindowRemaining(AuthRateLimitAction action, Duration window) {
        long windowMillis = window.toMillis();
        long nowMillis = Instant.now(clock).toEpochMilli();
        long windowStart = (nowMillis / windowMillis) * windowMillis;
        long windowEnd = windowStart + windowMillis;
        long remaining = windowEnd - nowMillis;
        return Duration.ofMillis(Math.max(remaining, 0));
    }
}
