package com.milestoneflow.shared.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7IdGeneratorTest {

    private final UuidV7IdGenerator generator = new UuidV7IdGenerator();

    @Test
    void shouldReturnNonNullUuid() {
        UUID id = generator.nextId();

        assertThat(id).isNotNull();
    }

    @Test
    void shouldReturnStandardUuid() {
        UUID id = generator.nextId();

        // Verify it is a valid UUID (string representation is 36 chars with hyphens)
        String str = id.toString();
        assertThat(str).hasSize(36);
        assertThat(str).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldGenerateUuidVersion7() {
        UUID id = generator.nextId();

        // UUID v7: version bits (bits 48-51 of the most significant 64 bits) = 0111 = 7
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void shouldGenerateIetfRfc4122Variant() {
        UUID id = generator.nextId();

        // Variant bits must be 10xx (IETF RFC 4122 variant = 2)
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void shouldGenerateUniqueIdsOverLargeBatch() {
        int count = 10_000;
        Set<UUID> ids = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            UUID id = generator.nextId();
            assertThat(ids).doesNotContain(id);
            ids.add(id);
        }

        assertThat(ids).hasSize(count);
    }

    @Test
    void shouldAllowFixedImplementationForTesting() {
        // Demonstrates that tests can use a fixed IdGenerator
        UUID fixedId = UUID.fromString("01912345-6789-7abc-def0-123456789abc");
        IdGenerator fixedGenerator = () -> fixedId;

        assertThat(fixedGenerator.nextId()).isEqualTo(fixedId);
        assertThat(fixedGenerator.nextId()).isEqualTo(fixedId);
    }

    @Test
    void shouldImplementIdGeneratorInterface() {
        assertThat(IdGenerator.class).isInterface();
        assertThat(generator).isInstanceOf(IdGenerator.class);
    }
}
