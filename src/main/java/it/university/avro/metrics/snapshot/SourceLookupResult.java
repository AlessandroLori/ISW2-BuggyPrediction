package it.university.avro.metrics.snapshot;

import java.util.Objects;

public record SourceLookupResult(
        String requestedPath,
        String resolvedPath,
        String sourceCode,
        boolean found,
        boolean exactMatch
) {
    public SourceLookupResult {
        requestedPath = Objects.requireNonNull(requestedPath, "requestedPath must not be null");
        resolvedPath = Objects.requireNonNull(resolvedPath, "resolvedPath must not be null");
        sourceCode = Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }

    public static SourceLookupResult notFound(final String requestedPath) {
        return new SourceLookupResult(requestedPath, requestedPath, "", false, false);
    }

    public static SourceLookupResult exact(final String requestedPath, final String sourceCode) {
        return new SourceLookupResult(requestedPath, requestedPath, sourceCode, true, true);
    }

    public static SourceLookupResult recovered(
            final String requestedPath,
            final String resolvedPath,
            final String sourceCode
    ) {
        return new SourceLookupResult(requestedPath, resolvedPath, sourceCode, true, false);
    }
}