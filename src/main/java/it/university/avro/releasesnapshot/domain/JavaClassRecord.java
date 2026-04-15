package it.university.avro.releasesnapshot.domain;

import java.util.Objects;

public record JavaClassRecord(
        String version,
        String classPath,
        String features,
        String nsmells,
        String buggy
) {
    public JavaClassRecord {
        version = Objects.requireNonNull(version, "version must not be null");
        classPath = Objects.requireNonNull(classPath, "classPath must not be null");
        features = Objects.requireNonNull(features, "features must not be null");
        nsmells = Objects.requireNonNull(nsmells, "nsmells must not be null");
        buggy = Objects.requireNonNull(buggy, "buggy must not be null");
    }
}