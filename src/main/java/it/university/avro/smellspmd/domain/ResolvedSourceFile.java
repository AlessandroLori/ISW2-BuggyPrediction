package it.university.avro.smellspmd.domain;

import java.util.Objects;

public record ResolvedSourceFile(
        String requestedClassPath,
        String resolvedClassPath,
        String sourceCode,
        boolean found,
        boolean exactMatch
) {
    public ResolvedSourceFile {
        requestedClassPath = Objects.requireNonNull(requestedClassPath, "requestedClassPath must not be null");
        resolvedClassPath = Objects.requireNonNull(resolvedClassPath, "resolvedClassPath must not be null");
        sourceCode = Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }

    public static ResolvedSourceFile notFound(final String requestedClassPath) {
        return new ResolvedSourceFile(requestedClassPath, requestedClassPath, "", false, false);
    }

    public static ResolvedSourceFile found(
            final String requestedClassPath,
            final String resolvedClassPath,
            final String sourceCode,
            final boolean exactMatch
    ) {
        return new ResolvedSourceFile(requestedClassPath, resolvedClassPath, sourceCode, true, exactMatch);
    }
}
