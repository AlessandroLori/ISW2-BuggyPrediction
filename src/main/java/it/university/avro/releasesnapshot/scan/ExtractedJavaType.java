package it.university.avro.releasesnapshot.scan;

import java.util.Objects;

public record ExtractedJavaType(
        String logicalClassPath,
        String typeName
) {
    public ExtractedJavaType {
        logicalClassPath = Objects.requireNonNull(logicalClassPath, "logicalClassPath must not be null");
        typeName = Objects.requireNonNull(typeName, "typeName must not be null");
    }
}