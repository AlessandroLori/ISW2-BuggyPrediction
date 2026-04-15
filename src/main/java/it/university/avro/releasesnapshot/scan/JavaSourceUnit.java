package it.university.avro.releasesnapshot.scan;

import java.util.Objects;

public record JavaSourceUnit(
        String archivePath,
        String sourceCode
) {
    public JavaSourceUnit {
        archivePath = Objects.requireNonNull(archivePath, "archivePath must not be null");
        sourceCode = Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }
}