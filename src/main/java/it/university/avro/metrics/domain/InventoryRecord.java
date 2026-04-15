package it.university.avro.metrics.domain;

import java.util.Objects;

public record InventoryRecord(
        String version,
        String classPath
) {
    public InventoryRecord {
        version = Objects.requireNonNull(version, "version must not be null");
        classPath = Objects.requireNonNull(classPath, "classPath must not be null");
    }
}