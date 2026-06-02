package it.university.avro.smellspmd.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ReleaseSourceSnapshot(
        Map<String, ResolvedSourceFile> sourcesByRequestedClassPath,
        Map<String, String> sourceByResolvedClassPath
) {

    public ReleaseSourceSnapshot {
        sourcesByRequestedClassPath = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(sourcesByRequestedClassPath, "sourcesByRequestedClassPath must not be null")
        ));
        sourceByResolvedClassPath = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(sourceByResolvedClassPath, "sourceByResolvedClassPath must not be null")
        ));
    }

    public ResolvedSourceFile sourceFor(final String requestedClassPath) {
        return sourcesByRequestedClassPath.getOrDefault(
                requestedClassPath,
                ResolvedSourceFile.notFound(requestedClassPath)
        );
    }
}
