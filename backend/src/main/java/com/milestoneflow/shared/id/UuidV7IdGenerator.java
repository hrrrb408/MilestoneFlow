package com.milestoneflow.shared.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link IdGenerator} producing RFC 9562 compliant UUID v7 values.
 *
 * <p>UUID v7 combines a Unix millisecond timestamp with random bits, producing
 * time-ordered identifiers that reduce B-tree index fragmentation compared to
 * UUID v4. This is an index-locality optimisation only — the timestamp embedded
 * in the UUID must <strong>not</strong> be treated as a business creation time.
 * Use {@code created_at} (populated via JPA auditing) for that purpose.
 *
 * <p>The underlying library ({@code java-uuid-generator}) is fully isolated
 * behind the {@link IdGenerator} interface. If the library needs to be replaced,
 * only this adapter class changes.
 *
 * <p>Thread-safe. Registered as the sole {@link IdGenerator} bean.
 *
 * @see IdGenerator
 */
@Component
public class UuidV7IdGenerator implements IdGenerator {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Override
    public UUID nextId() {
        return generator.generate();
    }
}
