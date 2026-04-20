package it.university.avro.smellspmd.source;

import it.university.avro.metrics.domain.ReleaseMetricsRecord;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.snapshot.JavaSourceLocator;
import it.university.avro.metrics.snapshot.SourceLookupResult;
import it.university.avro.smellspmd.domain.ReleaseSourceSnapshot;
import it.university.avro.smellspmd.domain.ResolvedSourceFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseSourceSnapshotBuilder {

    private final JavaSourceLocator sourceLocator;

    public ReleaseSourceSnapshotBuilder() {
        this.sourceLocator = new JavaSourceLocator();
    }

    public ReleaseSourceSnapshot build(
            final TemporaryGitRepository repository,
            final String tag,
            final List<ReleaseMetricsRecord> releaseRecords
    ) {
        final Map<String, ResolvedSourceFile> sourcesByRequestedClassPath = new LinkedHashMap<>();
        final Map<String, String> sourceByResolvedClassPath = new LinkedHashMap<>();

        for (ReleaseMetricsRecord releaseRecord : releaseRecords) {
            final String requestedClassPath = normalizePath(releaseRecord.classPath());

            if (sourcesByRequestedClassPath.containsKey(requestedClassPath)) {
                continue;
            }

            final SourceLookupResult lookupResult = sourceLocator.locate(repository, tag, requestedClassPath);
            if (!lookupResult.found()) {
                sourcesByRequestedClassPath.put(
                        requestedClassPath,
                        ResolvedSourceFile.notFound(requestedClassPath)
                );
                continue;
            }

            final String resolvedClassPath = normalizePath(lookupResult.resolvedPath());
            sourcesByRequestedClassPath.put(
                    requestedClassPath,
                    ResolvedSourceFile.found(
                            requestedClassPath,
                            resolvedClassPath,
                            lookupResult.sourceCode(),
                            lookupResult.exactMatch()
                    )
            );
            sourceByResolvedClassPath.putIfAbsent(resolvedClassPath, lookupResult.sourceCode());
        }

        return new ReleaseSourceSnapshot(sourcesByRequestedClassPath, sourceByResolvedClassPath);
    }

    private String normalizePath(final String path) {
        return path.replace('\\', '/');
    }
}
