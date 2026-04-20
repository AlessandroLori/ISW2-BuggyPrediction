package it.university.avro.smellspmd.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ReleaseSourceSnapshot {

    private final Map<String, ResolvedSourceFile> sourcesByRequestedClassPath;
    private final Map<String, String> sourceByResolvedClassPath;

    public ReleaseSourceSnapshot(
            final Map<String, ResolvedSourceFile> sourcesByRequestedClassPath,
            final Map<String, String> sourceByResolvedClassPath
    ) {
        this.sourcesByRequestedClassPath = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(sourcesByRequestedClassPath, "sourcesByRequestedClassPath must not be null")
        ));
        this.sourceByResolvedClassPath = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(sourceByResolvedClassPath, "sourceByResolvedClassPath must not be null")
        ));
    }

    public Map<String, ResolvedSourceFile> sourcesByRequestedClassPath() {
        return sourcesByRequestedClassPath;
    }

    public Map<String, String> sourceByResolvedClassPath() {
        return sourceByResolvedClassPath;
    }

    public ResolvedSourceFile sourceFor(final String requestedClassPath) {
        return sourcesByRequestedClassPath.getOrDefault(
                requestedClassPath,
                ResolvedSourceFile.notFound(requestedClassPath)
        );
    }
}
