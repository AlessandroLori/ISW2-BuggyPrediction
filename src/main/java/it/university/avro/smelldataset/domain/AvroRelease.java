package it.university.avro.smelldataset.domain;

import java.util.Objects;

public record AvroRelease(String version) {

    public AvroRelease {
        version = Objects.requireNonNull(version, "version must not be null").trim();
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
